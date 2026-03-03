/*
 * Copyright 2024 Moderne, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model;

import java.util.HashMap;
import java.util.Map;

public class AzureGalleryVMImage {

  private String name;
  private String galleryName;
  private String imageDefinitionName;
  private String version;
  private String resourceGroup;
  private String region;
  private String osType;
  private Map<String, String> tags;
  private String resourceId;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGalleryName() {
    return galleryName;
  }

  public void setGalleryName(String galleryName) {
    this.galleryName = galleryName;
  }

  public String getImageDefinitionName() {
    return imageDefinitionName;
  }

  public void setImageDefinitionName(String imageDefinitionName) {
    this.imageDefinitionName = imageDefinitionName;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getResourceGroup() {
    return resourceGroup;
  }

  public void setResourceGroup(String resourceGroup) {
    this.resourceGroup = resourceGroup;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getOsType() {
    return osType;
  }

  public void setOsType(String osType) {
    this.osType = osType;
  }

  public Map<String, String> getTags() {
    return tags != null ? tags : new HashMap<>();
  }

  public void setTags(Map<String, String> tags) {
    this.tags = tags;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }
}
