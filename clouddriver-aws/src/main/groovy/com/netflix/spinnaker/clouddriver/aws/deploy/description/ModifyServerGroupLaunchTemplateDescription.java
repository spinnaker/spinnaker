/*
 * Copyright 2020 Netflix, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

public class ModifyServerGroupLaunchTemplateDescription
    extends ModifyAsgLaunchConfigurationDescription {
  private Boolean requireIMDV2;
  private String kernelId;
  private String imageId;
  private Boolean associateIPv6Address;

  public Boolean getRequireIMDV2() {
    return requireIMDV2;
  }

  public void setRequireIMDV2(Boolean requireIMDV2) {
    this.requireIMDV2 = requireIMDV2;
  }

  public String getKernelId() {
    return kernelId;
  }

  public void setKernelId(String kernelId) {
    this.kernelId = kernelId;
  }

  public String getImageId() {
    return imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public Boolean getAssociateIPv6Address() {
    return associateIPv6Address;
  }

  public void setAssociateIPv6Address(boolean associateIPv6Address) {
    this.associateIPv6Address = associateIPv6Address;
  }
}
