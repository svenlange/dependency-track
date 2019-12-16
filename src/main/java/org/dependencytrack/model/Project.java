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
package org.dependencytrack.model;

import alpine.json.TrimmedStringDeserializer;
import alpine.validation.RegexSequence;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.Element;
import javax.jdo.annotations.Extension;
import javax.jdo.annotations.FetchGroup;
import javax.jdo.annotations.FetchGroups;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.Join;
import javax.jdo.annotations.Order;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import javax.jdo.annotations.Unique;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Model for tracking individual projects. Projects are high-level containers
 * of components and (optionally) other projects. Project are often software
 * applications, firmware, operating systems, or devices.
 *
 * @author Steve Springett
 * @since 3.0.0
 */
@PersistenceCapable
@FetchGroups({
        @FetchGroup(name = "ALL", members = {
                @Persistent(name = "name"),
                @Persistent(name = "version"),
                @Persistent(name = "uuid"),
                @Persistent(name = "parent"),
                @Persistent(name = "children"),
                @Persistent(name = "properties"),
                @Persistent(name = "tags")
        })
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Project implements Serializable {

    private static final long serialVersionUID = -7592438796591673355L;

    /**
     * Defines JDO fetch groups for this class.
     */
    public enum FetchGroup {
        ALL
    }

    @PrimaryKey
    @Persistent(valueStrategy = IdGeneratorStrategy.NATIVE)
    @JsonIgnore
    private long id;

    @Persistent
    @Index(name = "PROJECT_NAME_IDX")
    @Column(name = "NAME", jdbcType = "VARCHAR", allowsNull = "false")
    @NotBlank
    @Size(min = 1, max = 255)
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @Pattern(regexp = RegexSequence.Definition.PRINTABLE_CHARS, message = "The name may only contain printable characters")
    private String name;

    @Persistent
    @Column(name = "DESCRIPTION", jdbcType = "VARCHAR")
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @Pattern(regexp = RegexSequence.Definition.PRINTABLE_CHARS, message = "The description may only contain printable characters")
    private String description;

    @Persistent
    @Index(name = "PROJECT_VERSION_IDX")
    @Column(name = "VERSION", jdbcType = "VARCHAR")
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @Pattern(regexp = RegexSequence.Definition.PRINTABLE_CHARS, message = "The version may only contain printable characters")
    private String version;

    @Persistent
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @Pattern(regexp = RegexSequence.Definition.HTTP_URI, message = "The Package URL (purl) must be a valid URI and conform to https://github.com/package-url/purl-spec")
    private String purl;

    @Persistent(customValueStrategy = "uuid")
    @Unique(name = "PROJECT_UUID_IDX")
    @Column(name = "UUID", jdbcType = "VARCHAR", length = 36, allowsNull = "false")
    @NotNull
    private UUID uuid;

    @Persistent
    @Column(name = "PARENT_PROJECT_ID")
    private Project parent;

    @Persistent(mappedBy = "parent")
    private Collection<Project> children;

    @Persistent(mappedBy = "project", defaultFetchGroup = "true")
    @Order(extensions = @Extension(vendorName = "datanucleus", key = "list-ordering", value = "groupName ASC, propertyName ASC"))
    private List<ProjectProperty> properties;

    @Persistent(table = "PROJECTS_TAGS", defaultFetchGroup = "true", mappedBy = "projects")
    @Join(column = "PROJECT_ID")
    @Element(column = "TAG_ID")
    @Order(extensions = @Extension(vendorName = "datanucleus", key = "list-ordering", value = "name ASC"))
    private List<Tag> tags;

    /**
     * Convenience field which will contain the date of the last entry in the {@link Bom} table
     */
    @Persistent
    @Index(name = "PROJECT_LASTBOMIMPORT_IDX")
    @Column(name = "LAST_BOM_IMPORTED")
    private Date lastBomImport;

    /**
     * Convenience field which will contain the format of the last entry in the {@link Bom} table
     */
    @Persistent
    @Index(name = "PROJECT_LASTBOMIMPORT_FORMAT_IDX")
    @Column(name = "LAST_BOM_IMPORTED_FORMAT")
    private String lastBomImportFormat;

    /**
     * Convenience field which stores the Inherited Risk Score (IRS) of the last metric in the {@link ProjectMetrics} table
     */
    @Persistent
    @Index(name = "PROJECT_LAST_RISKSCORE_IDX")
    @Column(name = "LAST_RISKSCORE", allowsNull = "true") // New column, must allow nulls on existing databases))
    private Double lastInheritedRiskScore;

    @Persistent
    @Column(name = "ACTIVE")
    private Boolean active; // Added in v3.6. Existing records need to be nullable on upgrade.

    private transient ProjectMetrics metrics;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPurl() {
        return purl;
    }

    public void setPurl(String purl) {
        this.purl = purl;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Project getParent() {
        return parent;
    }

    public void setParent(Project parent) {
        this.parent = parent;
    }

    public Collection<Project> getChildren() {
        return children;
    }

    public void setChildren(Collection<Project> children) {
        this.children = children;
    }

    public List<ProjectProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<ProjectProperty> properties) {
        this.properties = properties;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public Date getLastBomImport() {
        return lastBomImport;
    }

    public void setLastBomImport(Date lastBomImport) {
        this.lastBomImport = lastBomImport;
    }

    public String getLastBomImportFormat() {
        return lastBomImportFormat;
    }

    public void setLastBomImportFormat(String lastBomImportFormat) {
        this.lastBomImportFormat = lastBomImportFormat;
    }

    public Double getLastInheritedRiskScore() {
        return lastInheritedRiskScore;
    }

    public void setLastInheritedRiskScore(Double lastInheritedRiskScore) {
        this.lastInheritedRiskScore = lastInheritedRiskScore;
    }

    public boolean isActive() {
        if (active == null) {
            return true;
        }
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public ProjectMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(ProjectMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public String toString() {
        if (getPurl() != null) {
            return getPurl();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(getName());
            if (getVersion() != null) {
                sb.append(" : ").append(getVersion());
            }
            return sb.toString();
        }
    }
}
