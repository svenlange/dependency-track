/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.parser.spdx.rdf;

import alpine.logging.Logger;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dependencytrack.exception.ParseException;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.License;
import org.dependencytrack.persistence.QueryManager;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXDocumentFactory;
import org.spdx.rdfparser.SpdxDocumentContainer;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ConjunctiveLicenseSet;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.SpdxListedLicense;
import org.spdx.rdfparser.model.Checksum;
import org.spdx.rdfparser.model.DoapProject;
import org.spdx.rdfparser.model.SpdxDocument;
import org.spdx.rdfparser.model.SpdxFile;
import org.spdx.rdfparser.model.SpdxPackage;
import org.spdx.tools.TagToRDF;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser of SPDX RDF and Tag documents.
 *
 * @author Steve Springett
 * @since 3.0.0
 */
public class SpdxDocumentParser {

    private static final Logger LOGGER = Logger.getLogger(SpdxDocumentParser.class);

    public enum DocumentType {
        RDF,
        TAG
    }

    private String specVersion = null;
    private final QueryManager qm;

    public SpdxDocumentParser(final QueryManager qm) {
        this.qm = qm;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public static boolean isSupportedSpdxFormat(final String spdxString) {
        if (spdxString.contains("<rdf:RDF") && spdxString.contains("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
            return true;
        } else if (spdxString.contains("SPDXVersion:")) {
            return true;
        }
        return false;
    }

    public List<Component> parse(final byte[] spdx) throws ParseException {
        specVersion = null;
        final String spdxString = new String(spdx, StandardCharsets.UTF_8);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(spdx)) {
            if (spdxString.contains("<rdf:RDF") && spdxString.contains("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                return parse(inputStream, DocumentType.RDF);
            } else if (spdxString.contains("SPDXVersion:")) {
                return parse(inputStream, DocumentType.TAG);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing SPDX document", e);
        }
        return Collections.emptyList();
    }

    /*
    Deprecation warnings are suppressed due to deprecation of specific attributes in the SPDX specification, not due to
    the methods being replaced with something else. In order to support backward compatibility with SPDX 2.0 and lower,
    use of the deprecated methods is required.
    */
    @SuppressWarnings("deprecation")
    public List<Component> parse(final InputStream inputStream, final DocumentType type) throws ParseException {
        specVersion = null;
        /*
         * Attempt to read in the document.
         */
        final SpdxDocument doc;

        if (type == DocumentType.TAG) {
            try {
                final List<String> warnings = new ArrayList<>();
                final SpdxDocumentContainer docContainer = TagToRDF.convertTagFileToRdf(inputStream, "RDF/XML", warnings);
                doc = docContainer.getSpdxDocument();
                specVersion = doc.getSpecVersion();
                for (final String warning: warnings) {
                    LOGGER.warn(warning);
                }
            } catch (Exception e) {
                LOGGER.warn("Unable to parse SPDX Document", e);
                throw new ParseException("Unable to parse SPDX Document", e);
            }
        } else {
            try {
                doc = SPDXDocumentFactory.createSpdxDocument(inputStream, "http://spdx.org", "RDF/XML");
                specVersion = doc.getSpecVersion();
            } catch (InvalidSPDXAnalysisException e) {
                LOGGER.warn("Unable to read SPDX RDF Document", e);
                throw new ParseException("Unable to read SPDX RDF Document", e);
            }
        }

        /*
         * Verify the document is valid and throw an exception with cause(s) if not.
         */
        final List<String> verify = doc.verify();
        if (CollectionUtils.isNotEmpty(verify)) {
            LOGGER.warn("The SPDX Document has " + verify.size() + " parsing exception(s)");
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < verify.size(); i++) {
                LOGGER.warn(verify.get(i));
                sb.append("[").append(verify.get(i)).append("]");
                if (i < verify.size() + 1) {
                    sb.append(",");
                }
            }
            throw new ParseException("The SPDX Document has " + verify.size() + " parsing exception(s): " + sb.toString());
        }

        LOGGER.info("Processing SPDX Document version " + doc.getSpecVersion());

        /*
         * Document is valid. Let's do something interesting.
         */
        final List<Component> components = new ArrayList<>();
        try {
            final List<SpdxPackage> allPackages = doc.getDocumentContainer().findAllPackages();
            final List<SpdxFile> allFiles = doc.getDocumentContainer().findAllFiles();

            for (final SpdxPackage spdxPackage: allPackages) {
                final Component component = new Component();
                component.setName(StringUtils.trimToNull(spdxPackage.getName()));
                component.setDescription(StringUtils.trimToNull(spdxPackage.getDescription()));
                component.setGroup(StringUtils.trimToNull(spdxPackage.getOriginator()));
                if (component.getGroup() == null) {
                    component.setGroup(StringUtils.trimToNull(spdxPackage.getSupplier()));
                }
                component.setFilename(StringUtils.trimToNull(spdxPackage.getPackageFileName()));
                component.setVersion(StringUtils.trimToNull(spdxPackage.getVersionInfo()));
                // Populate checksums
                composeChecksums(component, spdxPackage.getChecksums());
                component.setCopyright(StringUtils.trimToNull(spdxPackage.getCopyrightText()));
                // Process licenses - the package authors declared license always takes priority
                final AnyLicenseInfo declaredLicense = spdxPackage.getLicenseDeclared();
                final AnyLicenseInfo concludedLicense = spdxPackage.getLicenseConcluded();
                if (declaredLicense != null) {
                    processLicenses(component, declaredLicense);
                } else if (concludedLicense != null) {
                    processLicenses(component, concludedLicense);
                }
                components.add(component);
            }

            for (final SpdxFile spdxFile: allFiles) {
                final Component component = new Component();
                component.setFilename(spdxFile.getName());
                component.setName(spdxFile.getName());
                // Populate checksums
                composeChecksums(component, spdxFile.getChecksums());
                component.setCopyright(StringUtils.trimToNull(spdxFile.getCopyrightText()));
                // Process licenses
                final AnyLicenseInfo concludedLicense = spdxFile.getLicenseConcluded();
                processLicenses(component, concludedLicense);
                // artifactOf is deprecated in SPDX 2.1
                for (final DoapProject project: spdxFile.getArtifactOf()) {
                    component.setName(project.getName());
                }
                components.add(component);
            }
        } catch (InvalidSPDXAnalysisException e) {
            LOGGER.error("An error occurred while processing a valid SPDX document", e);
        }
        return components;
    }

    private void composeChecksums(final Component component, final Checksum[] checksums) {
        for (final Checksum checksum : checksums) {
            final Checksum.ChecksumAlgorithm alg = checksum.getAlgorithm();
            if (alg == Checksum.ChecksumAlgorithm.checksumAlgorithm_md5) {
                component.setMd5(StringUtils.trimToNull(checksum.getValue()));
            } else if (alg == Checksum.ChecksumAlgorithm.checksumAlgorithm_sha1) {
                component.setSha1(StringUtils.trimToNull(checksum.getValue()));
            } else if (alg == Checksum.ChecksumAlgorithm.checksumAlgorithm_sha256) {
                component.setSha256(StringUtils.trimToNull(checksum.getValue()));
            }
        }
    }

    private void processLicenses(final Component component, final AnyLicenseInfo rootAnyLicenseInfo) {
        if (rootAnyLicenseInfo instanceof ConjunctiveLicenseSet) { // two more more licenses defined
            final AnyLicenseInfo[] licenseInfos = ((ConjunctiveLicenseSet) rootAnyLicenseInfo).getFlattenedMembers();
            for (final AnyLicenseInfo licenseInfo : licenseInfos) {
                if (licenseInfo instanceof ExtractedLicenseInfo) {
                    final ExtractedLicenseInfo extractedLicenseInfo = (ExtractedLicenseInfo) licenseInfo;
                    component.setLicense(extractedLicenseInfo.getName());
                } else if (licenseInfo instanceof SpdxListedLicense) {
                    processSpdxListedLicense(component, (SpdxListedLicense) licenseInfo);
                }
            }
            //component.setLicense(rootAnyLicenseInfo.toString());
        } else if (rootAnyLicenseInfo instanceof SpdxListedLicense) {
            processSpdxListedLicense(component, (SpdxListedLicense) rootAnyLicenseInfo);
        }
    }

    private void processSpdxListedLicense(final Component component, final SpdxListedLicense spdxListedLicense) {
        final License license = qm.getLicense(spdxListedLicense.getLicenseId());
        if (license != null) {
            component.setResolvedLicense(license);
        } else {
            component.setLicense(spdxListedLicense.getName());
        }
    }
}
