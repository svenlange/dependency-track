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
package org.dependencytrack.resources.v1;

import alpine.auth.PermissionRequired;
import alpine.event.framework.Event;
import alpine.persistence.PaginatedResult;
import alpine.resources.AlpineResource;
import alpine.validation.RegexSequence;
import alpine.validation.ValidationTask;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.ResponseHeader;
import org.apache.commons.lang3.StringUtils;
import org.dependencytrack.auth.Permissions;
import org.dependencytrack.event.InternalComponentIdentificationEvent;
import org.dependencytrack.event.RepositoryMetaEvent;
import org.dependencytrack.event.VulnerabilityAnalysisEvent;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.License;
import org.dependencytrack.persistence.QueryManager;
import org.dependencytrack.util.InternalComponentIdentificationUtil;

import javax.jdo.Query;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.regex.Pattern;

/**
 * JAX-RS resources for processing components.
 *
 * @author Steve Springett
 * @since 3.0.0
 */
@Path("/v1/component")
@Api(value = "component", authorizations = @Authorization(value = "X-Api-Key"))
public class ComponentResource extends AlpineResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a list of all components",
            response = Component.class,
            responseContainer = "List",
            responseHeaders = @ResponseHeader(name = TOTAL_COUNT_HEADER, response = Long.class, description = "The total number of components")
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response getAllComponents() {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final PaginatedResult result = qm.getComponents(true);
            return Response.ok(result.getObjects()).header(TOTAL_COUNT_HEADER, result.getTotal()).build();
        }
    }

    @GET
    @Path("/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a specific component",
            response = Component.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The component could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response getComponentByUuid(
            @ApiParam(value = "The UUID of the component to retrieve", required = true)
            @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            final Component component = qm.getObjectByUuid(Component.class, uuid);
            if (component != null) {
                return Response.ok(component).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The component could not be found.").build();
            }
        }
    }

    @GET
    @Path("/hash/{hash}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a specific component",
            response = Component.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The component could not be found")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response getComponentByHash(
            @ApiParam(value = "The MD5, SHA-1, SHA-256, SHA-512, SHA3-256, or SHA3-512 hash of the component to retrieve", required = true)
            @PathParam("hash") String hash) {
        try (QueryManager qm = new QueryManager()) {
            failOnValidationError(
                    new ValidationTask(RegexSequence.Pattern.HASH_MD5_SHA1_SHA256_SHA512, hash, "Invalid MD5, SHA-1, SHA-256, SHA-512, SHA3-256, or SHA3-512 hash.")
            );
            final Component component = qm.getComponentByHash(hash);
            if (component != null) {
                return Response.ok(component).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The component could not be found.").build();
            }
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Creates a new component",
            response = Component.class,
            code = 201
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.PORTFOLIO_MANAGEMENT)
    public Response createComponent(Component jsonComponent) {
        final Validator validator = super.getValidator();
        failOnValidationError(
                validator.validateProperty(jsonComponent, "name"),
                validator.validateProperty(jsonComponent, "version"),
                validator.validateProperty(jsonComponent, "group"),
                validator.validateProperty(jsonComponent, "description"),
                validator.validateProperty(jsonComponent, "license"),
                validator.validateProperty(jsonComponent, "filename"),
                validator.validateProperty(jsonComponent, "classifier"),
                validator.validateProperty(jsonComponent, "cpe"),
                validator.validateProperty(jsonComponent, "copyright"),
                validator.validateProperty(jsonComponent, "md5"),
                validator.validateProperty(jsonComponent, "sha1"),
                validator.validateProperty(jsonComponent, "sha256"),
                validator.validateProperty(jsonComponent, "sha512"),
                validator.validateProperty(jsonComponent, "sha3_256"),
                validator.validateProperty(jsonComponent, "sha3_512")
        );

        try (QueryManager qm = new QueryManager()) {
            Component parent = null;
            if (jsonComponent.getParent() != null && jsonComponent.getParent().getUuid() != null) {
                parent = qm.getObjectByUuid(Component.class, jsonComponent.getParent().getUuid());
            }

            final License resolvedLicense = qm.getLicense(jsonComponent.getLicense());
            Component component = new Component();
            component.setName(StringUtils.trimToNull(jsonComponent.getName()));
            component.setVersion(StringUtils.trimToNull(jsonComponent.getVersion()));
            component.setGroup(StringUtils.trimToNull(jsonComponent.getGroup()));
            component.setDescription(StringUtils.trimToNull(jsonComponent.getDescription()));
            component.setFilename(StringUtils.trimToNull(jsonComponent.getFilename()));
            component.setClassifier(jsonComponent.getClassifier());
            component.setPurl(jsonComponent.getPurl());
            component.setInternal(InternalComponentIdentificationUtil.isInternalComponent(component, qm));
            component.setCpe(StringUtils.trimToNull(jsonComponent.getCpe()));
            component.setCopyright(StringUtils.trimToNull(jsonComponent.getCopyright()));
            component.setMd5(StringUtils.trimToNull(jsonComponent.getMd5()));
            component.setSha1(StringUtils.trimToNull(jsonComponent.getSha1()));
            component.setSha256(StringUtils.trimToNull(jsonComponent.getSha256()));
            component.setSha512(StringUtils.trimToNull(jsonComponent.getSha512()));
            component.setSha3_256(StringUtils.trimToNull(jsonComponent.getSha3_256()));
            component.setSha3_512(StringUtils.trimToNull(jsonComponent.getSha3_512()));
            if (resolvedLicense != null) {
                component.setLicense(null);
                component.setResolvedLicense(resolvedLicense);
            } else {
                component.setLicense(StringUtils.trimToNull(jsonComponent.getLicense()));
                component.setResolvedLicense(null);
            }
            component.setParent(parent);

            component = qm.createComponent(component, true);
            Event.dispatch(new VulnerabilityAnalysisEvent(component));
            Event.dispatch(new RepositoryMetaEvent(component));
            return Response.status(Response.Status.CREATED).entity(component).build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Updates a component",
            response = Component.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The UUID of the component could not be found"),
    })
    @PermissionRequired(Permissions.Constants.PORTFOLIO_MANAGEMENT)
    public Response updateComponent(Component jsonComponent) {
        final Validator validator = super.getValidator();
        failOnValidationError(
                validator.validateProperty(jsonComponent, "name"),
                validator.validateProperty(jsonComponent, "version"),
                validator.validateProperty(jsonComponent, "group"),
                validator.validateProperty(jsonComponent, "description"),
                validator.validateProperty(jsonComponent, "license"),
                validator.validateProperty(jsonComponent, "filename"),
                validator.validateProperty(jsonComponent, "classifier"),
                validator.validateProperty(jsonComponent, "cpe"),
                validator.validateProperty(jsonComponent, "copyright"),
                validator.validateProperty(jsonComponent, "md5"),
                validator.validateProperty(jsonComponent, "sha1"),
                validator.validateProperty(jsonComponent, "sha256"),
                validator.validateProperty(jsonComponent, "sha512"),
                validator.validateProperty(jsonComponent, "sha3_256"),
                validator.validateProperty(jsonComponent, "sha3_512")
        );
        try (QueryManager qm = new QueryManager()) {
            Component component = qm.getObjectByUuid(Component.class, jsonComponent.getUuid());
            if (component != null) {
                // Name cannot be empty or null - prevent it
                final String name = StringUtils.trimToNull(jsonComponent.getName());
                if (name != null) {
                    component.setName(name);
                }
                component.setVersion(StringUtils.trimToNull(jsonComponent.getVersion()));
                component.setGroup(StringUtils.trimToNull(jsonComponent.getGroup()));
                component.setDescription(StringUtils.trimToNull(jsonComponent.getDescription()));
                component.setFilename(StringUtils.trimToNull(jsonComponent.getFilename()));
                component.setClassifier(jsonComponent.getClassifier());
                component.setPurl(jsonComponent.getPurl());
                component.setInternal(InternalComponentIdentificationUtil.isInternalComponent(component, qm));
                component.setCpe(StringUtils.trimToNull(jsonComponent.getCpe()));
                component.setCopyright(StringUtils.trimToNull(jsonComponent.getCopyright()));
                component.setMd5(StringUtils.trimToNull(jsonComponent.getMd5()));
                component.setSha1(StringUtils.trimToNull(jsonComponent.getSha1()));
                component.setSha256(StringUtils.trimToNull(jsonComponent.getSha256()));
                component.setSha512(StringUtils.trimToNull(jsonComponent.getSha512()));
                component.setSha3_256(StringUtils.trimToNull(jsonComponent.getSha3_256()));
                component.setSha3_512(StringUtils.trimToNull(jsonComponent.getSha3_512()));

                final License resolvedLicense = qm.getLicense(jsonComponent.getLicense());
                if (resolvedLicense != null) {
                    component.setLicense(null);
                    component.setResolvedLicense(resolvedLicense);
                } else {
                    component.setLicense(StringUtils.trimToNull(jsonComponent.getLicense()));
                    component.setResolvedLicense(null);
                }

                component = qm.updateComponent(component, true);
                Event.dispatch(new VulnerabilityAnalysisEvent(component));
                Event.dispatch(new RepositoryMetaEvent(component));
                return Response.ok(component).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The UUID of the component could not be found.").build();
            }
        }
    }

    @DELETE
    @Path("/{uuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Deletes a component",
            code = 204
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The UUID of the component could not be found")
    })
    @PermissionRequired(Permissions.Constants.PORTFOLIO_MANAGEMENT)
    public Response deleteComponent(
            @ApiParam(value = "The UUID of the component to delete", required = true)
            @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            final Component component = qm.getObjectByUuid(Component.class, uuid, Component.FetchGroup.ALL.name());
            if (component != null) {
                qm.recursivelyDelete(component, false);
                qm.commitSearchIndex(Component.class);
                return Response.status(Response.Status.NO_CONTENT).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The UUID of the component could not be found.").build();
            }
        }
    }

    @GET
    @Path("/internal/identify")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Requests the identification of internal components in the portfolio", code = 204)
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
    })
    @PermissionRequired(Permissions.Constants.SYSTEM_CONFIGURATION)
    public Response identifyInternalComponents() {
        Event.dispatch(new InternalComponentIdentificationEvent());
        return Response.status(Response.Status.NO_CONTENT).build();
    }

}
