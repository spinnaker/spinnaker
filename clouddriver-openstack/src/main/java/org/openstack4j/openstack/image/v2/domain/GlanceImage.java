/**
 * This file is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2015-16 Jeremy Unruh, ContainX, and OpenStack4j
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.openstack4j.openstack.image.v2.domain;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstack4j.model.common.builder.BasicResourceBuilder;
import org.openstack4j.model.image.v2.ContainerFormat;
import org.openstack4j.model.image.v2.DiskFormat;
import org.openstack4j.model.image.v2.Image;
import org.openstack4j.model.image.v2.builder.ImageBuilder;
import org.openstack4j.openstack.common.ListResult;
import org.openstack4j.openstack.common.Metadata;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/*
 * Works around https://github.com/spinnaker/spinnaker/issues/3018
 *
 * TODO: Remove this file after openstack4j fixes this issue upstream. Any release that includes
 * [this commit](https://github.com/ContainX/openstack4j/commit/bb78146aa914b855c2d77e2b4d42e455190fb8eb) should do it.
 *
 * This source file is included in the spinnaker/clouddriver project only because an important fix from openstack4j has
 * not yet been released. After [this commit](https://github.com/ContainX/openstack4j/commit/bb78146aa914b855c2d77e2b4d42e455190fb8eb)
 * makes it into a proper release, this file and org.openstack4j.model.image.v2.Image should be removed, and the
 * spinnaker dependencies updated to include that new openstack4j release.
 */

/**
 * A glance v2.0-2.3 image model implementation
 *
 * @author emjburns
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlanceImage implements Image {

  private static final Set<String> RESERVED_KEYS = Sets.newHashSet(Arrays.asList(new String[] {
    "id",
    "name",
    "tags",
    "status",
    "container_format",
    "disk_format",
    "created_at",
    "updated_at",
    "min_disk",
    "min_ram",
    "protected",
    "checksum",
    "owner",
    "visibility",
    "size",
    "locations",
    "direct_url",
    "self",
    "file",
    "schema",
    "architecture",
    "instance_uuid",
    "kernel_id",
    "os_version",
    "os_distro",
    "ramdisk_id",
    "virtual_size" }));

  private static final long serialVersionUID = 1L;

  private String id;

  private String name;

  private List<String> tags;

  private ImageStatus status;

  @JsonProperty("container_format")
  private ContainerFormat containerFormat;

  @JsonProperty("disk_format")
  private DiskFormat diskFormat;

  @JsonProperty("created_at")
  private Date createdAt;

  @JsonProperty("updated_at")
  private Date updatedAt;

  @JsonProperty("min_disk")
  private Long minDisk;

  @JsonProperty("min_ram")
  private Long minRam;

  @JsonProperty("protected")
  private Boolean isProtected;

  private String checksum;

  private String owner;

  private ImageVisibility visibility;

  private Long size;

  private List<Location> locations;

  @JsonProperty("direct_url")
  private String directUrl;

  private String self;

  private String file;

  private String schema;

  private String architecture;

  @JsonProperty("instance_uuid")
  private String instanceUuid;

  @JsonProperty("kernel_id")
  private String kernelId;

  @JsonProperty("os_version")
  private String osVersion;

  @JsonProperty("os_distro")
  private String osDistro;

  @JsonProperty("ramdisk_id")
  private String ramdiskId;

  @JsonProperty("virtual_size")
  private Long virtualSize;

  private Map<String, String> additionalProperties = Maps.newHashMap();

  /**
   * {@inheritDoc}
   */
  @Override
  public void setName(String name) {
    this.name = name;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setId(String id) {
    this.id = id;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ImageStatus getStatus() {
    return status;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getTags() {
    return tags;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ContainerFormat getContainerFormat() {
    return containerFormat;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Date getCreatedAt() {
    return createdAt;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DiskFormat getDiskFormat() {
    return diskFormat;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Date getUpdatedAt() {
    return updatedAt;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getMinDisk() {
    return minDisk;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Boolean getIsProtected() {
    return isProtected;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getId() {
    return id;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getMinRam() {
    return minRam;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getChecksum() {
    return checksum;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getOwner() {
    return owner;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ImageVisibility getVisibility() {
    return visibility;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getSize() {
    return size;
  }

  @Override
  public List<Location> getLocations() {
    return locations;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDirectUrl() {
    return directUrl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getSelf() {
    return self;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getFile() {
    return file;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getSchema() {
    return schema;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getRamdiskId() {
    return ramdiskId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getOsDistro() {
    return osDistro;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getOsVersion() {
    return osVersion;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getKernelId() {
    return kernelId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getInstanceUuid() {
    return instanceUuid;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getArchitecture() {
    return architecture;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getVirtualSize() {
    return virtualSize;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getAdditionalPropertyValue(String key) {
    return additionalProperties.get(key);
  }

  @JsonAnyGetter
  public Map<String, String> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String key, String value) {
    if (key != null && !RESERVED_KEYS.contains(key)) {
      additionalProperties.put(key, value);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ImageBuilder toBuilder() {
    return new ImageConcreteBuilder(this);
  }

  public static ImageBuilder builder() {
    return new ImageConcreteBuilder();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("id", id)
      .add("name", name)
      .add("tags", tags)
      .add("imageStatus", status)
      .add("containerFormat", containerFormat)
      .add("diskFormat", diskFormat)
      .add("createdAt", createdAt)
      .add("updatedAt", updatedAt)
      .add("minDisk", minDisk)
      .add("minRam", minRam)
      .add("isProtected", isProtected)
      .add("checksum", checksum)
      .add("owner", owner)
      .add("visibility", visibility)
      .add("size", size)
      .add("locations", locations)
      .add("directUrl", directUrl)
      .add("self", self)
      .add("file", file)
      .add("schema", schema)
      .add("architecture", architecture)
      .add("instanceUuid", instanceUuid)
      .add("kernelId", kernelId)
      .add("osVersion", osVersion)
      .add("osDistro", osDistro)
      .add("ramdiskId", ramdiskId)
      .add("virtualSize", virtualSize)
      .toString();
  }


  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Images extends ListResult<GlanceImage> {
    private static final long serialVersionUID = 1L;
    @JsonProperty("images")
    private List<GlanceImage> images;

    @Override
    protected List<GlanceImage> value() {
      return images;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Location {
    @JsonProperty("url")
    private String url;

    @JsonProperty("metadata")
    private Metadata metadata;

    public String getUrl() {
      return url;
    }

    public Metadata getMetadata() {
      return metadata;
    }

  }

  public static class ImageConcreteBuilder extends BasicResourceBuilder<Image, ImageConcreteBuilder> implements ImageBuilder {
    private GlanceImage m;

    ImageConcreteBuilder() {
      this(new GlanceImage());
    }

    ImageConcreteBuilder(GlanceImage m) {
      this.m = m;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder visibility(ImageVisibility visibility) {
      m.visibility = visibility;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder tags(List<String> tags) {
      m.tags = tags;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder containerFormat(ContainerFormat containerFormat) {
      m.containerFormat = containerFormat;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder diskFormat(DiskFormat diskFormat) {
      m.diskFormat = diskFormat;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder minDisk(Long minDisk) {
      m.minDisk = minDisk;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder minRam(Long minRam) {
      m.minRam = minRam;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder isProtected(Boolean isProtected) {
      m.isProtected = isProtected;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder architecture(String architecture) {
      m.architecture = architecture;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder instanceUuid(String instanceUuid) {
      m.instanceUuid = instanceUuid;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder kernelId(String kernelId) {
      m.kernelId = kernelId;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder osVersion(String osVersion) {
      m.osVersion = osVersion;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder osDistro(String osDistro) {
      m.osDistro = osDistro;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder ramdiskId(String ramdiskId) {
      m.ramdiskId = ramdiskId;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder additionalProperty(String key, String value) {
      if (key != null && !RESERVED_KEYS.contains(key)) {
        m.additionalProperties.put(key, value);
      }
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image build() {
      return m;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageBuilder from(Image in) {
      m = (GlanceImage) in;
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Image reference() {
      return m;
    }
  }
}
