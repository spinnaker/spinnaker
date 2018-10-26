package org.openstack4j.model.image.v2;
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

import java.util.Date;
import java.util.List;

import org.openstack4j.common.Buildable;
import org.openstack4j.model.common.BasicResource;
import org.openstack4j.model.image.v2.builder.ImageBuilder;
import org.openstack4j.openstack.image.v2.domain.GlanceImage.Location;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/*
 * Works around https://github.com/spinnaker/spinnaker/issues/3018
 *
 * TODO: Remove this file after openstack4j fixes this issue upstream. Any release that includes
 * [this commit](https://github.com/ContainX/openstack4j/commit/bb78146aa914b855c2d77e2b4d42e455190fb8eb) should do it.
 *
 * This source file is included in the spinnaker/clouddriver project only because an important fix from openstack4j has
 * not yet been released. After [this commit](https://github.com/ContainX/openstack4j/commit/bb78146aa914b855c2d77e2b4d42e455190fb8eb)
 * makes it into a proper release, this file and org.openstack4j.openstack.image.v2.domain.GlanceImage should be removed, and the
 * spinnaker dependencies updated to include that new openstack4j release.
 */

/**
 * A Glance v2.0-2.3 Image
 * @author emjburns
 * @see http://developer.openstack.org/api-ref-image-v2.html#showImage-v2
 */
public interface Image extends BasicResource, Buildable<ImageBuilder> {

  public enum ImageStatus {
    /**
     * Image status is not one of the documented options.
     * http://docs.openstack.org/developer/glance/statuses.html
     */
    UNRECOGNIZED,
    /**
     * The image identifier has been reserved for an image in the Glance registry.
     * No image data has been uploaded to Glance and the image size
     * was not explicitly set to zero on creation.
     */
    QUEUED,
    /**
     * Denotes that an image’s raw data is currently being uploaded to Glance.
     * When an image is registered with a call to POST /images and there is an
     * x-image-meta-location header present, that image will never be in the saving status
     * (as the image data is already available in some other location).
     */
    SAVING,
    /**
     * Denotes an image that is fully available in Glance. This occurs when the
     * image data is uploaded, or the image size is explicitly set to zero on creation.
     */
    ACTIVE,
    /**
     * Denotes that access to image data is not allowed to any non-admin user.
     * Prohibiting downloads of an image also prohibits operations like image
     * export and image cloning that may require image data.
     */
    DEACTIVATED,
    /**
     * Denotes that an error occurred during the uploading of an image’s data,
     * and that the image is not readable.
     */
    KILLED,
    /**
     * Glance has retained the information about the image, but it is no longer
     * available to use. An image in this state will be removed automatically at a later date.
     */
    DELETED,
    /**
     * This is similar to deleted, however, Glance has not yet removed
     * the image data. An image in this state is not recoverable.
     */
    PENDING_DELETE;

    @JsonCreator
    public static ImageStatus value(String v)
    {
      if (v == null) return UNRECOGNIZED;
      try {
        return valueOf(v.toUpperCase());
      } catch (IllegalArgumentException e) {
        return UNRECOGNIZED;
      }
    }

    @JsonValue
    public String value() {
      return name().toLowerCase();
    }
  }

  public enum ImageVisibility {
    PUBLIC,
    PRIVATE,
    UNKNOWN;

    @JsonCreator
    public static ImageVisibility forValue(String value) {
      if (value != null)
      {
        for (ImageVisibility s : ImageVisibility.values()) {
          if (s.name().equalsIgnoreCase(value)) {
            return s;
          }
        }
      }
      return ImageVisibility.UNKNOWN;
    }

    @JsonValue
    public String value() {
      return name().toLowerCase();
    }
  }

  /**
   * @return image status
   */
  ImageStatus getStatus();

  /**
   * @return image name.
   */
  String getName();

  /**
   * @return a list of tag objects
   */
  List<String> getTags();

  /**
   * @return the container format of the image
   */
  ContainerFormat getContainerFormat();

  /**
   * @return the ISO 8601 date and time when the resource was created
   */
  Date getCreatedAt();

  /**
   * @return the disk format of the image
   */
  DiskFormat getDiskFormat();

  /**
   * @return the ISO 8601 date and time when the resource was updated
   */
  Date getUpdatedAt();

  /**
   * @return the minimum disk size in GB that is required to boot the image
   */
  Long getMinDisk();

  /**
   * @return image protection for deletion
   * Default is false
   */
  Boolean getIsProtected();

  /**
   * @return UUID of the image
   */
  String getId();

  /**
   * @return the minimum amount of RAM in MB that is required to boot the image
   */
  Long getMinRam();

  /**
   * @return hash that is used over the image data
   * (image service uses this value for verification)
   */
  String getChecksum();

  /**
   * @return the id of teh owner, or tenant, of the image
   */
  String getOwner();

  /**
   * @return image visibility (public or private)
   * Default is private
   */
  ImageVisibility getVisibility();

  /**
   * @return the size of the image data, in bytes
   */
  Long getSize();

  /**
   * @return A list of URLs to access the image file in external store.
   *
   * This list appears if the show_multiple_locations option is
   * set to true in the Image service's configuration file.
   */
  List<Location> getLocations();

  /**
   * @return the URL to access the image file kept in external store
   * This value appears when you set {@code show_image_direct_url} option to
   * {@code true} in the image service's configuration file
   */
  String getDirectUrl();

  /**
   * @return the URL for the virtual machine image
   */
  String getSelf();

  /**
   * @return the URL for the virtual machine image file
   */
  String getFile();

  /**
   * @return the URL for the schema of teh virtual machine image
   */
  String getSchema();

  /**
   * Pattern: ^([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}$
   * @return ID of image stored in Glance that should be used as the ramdisk when booting an AMI-style image.
   */
  String getRamdiskId();

  /**
   * @return Common name of operating system distribution as specified in
   * http://docs.openstack.org/trunk/openstack-compute/admin/content/adding-images.html
   */
  String getOsDistro();

  /**
   * @return Operating system version as specified by the distributor
   */
  String getOsVersion();

  /**
   * Pattern: ^([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}$
   * @return ID of image stored in Glance that should be used as the kernel when booting an AMI-style image
   */
  String getKernelId();

  /**
   * @return ID of instance used to create this image
   */
  String getInstanceUuid();

  /**
   * @return Operating system architecture as specified in
   * http://docs.openstack.org/trunk/openstack-compute/admin/content/adding-images.html
   */
  String getArchitecture();

  /**
   * @return Virtual size of image in bytes (READ-ONLY)
   */
  Long getVirtualSize();

  /**
   * @return Additional property's value from key
   * https://developer.openstack.org/api-ref/image/v2/index.html#create-an-image
   */
  String getAdditionalPropertyValue(String key);
}
