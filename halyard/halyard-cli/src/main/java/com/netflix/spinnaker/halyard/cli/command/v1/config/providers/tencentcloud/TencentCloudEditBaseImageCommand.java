/*
 * Copyright 2020 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.tencentcloud;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudBaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudBaseImage.TencentCloudVirtualizationSettings;

@Parameters(separators = "=")
public class TencentCloudEditBaseImageCommand
    extends AbstractEditBaseImageCommand<TencentCloudBaseImage> {

  @Override
  protected String getProviderName() {
    return "tencentcloud";
  }

  @Parameter(
      names = "--region",
      required = true,
      description = TencentCloudCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Parameter(
      names = "--zone",
      required = true,
      description = TencentCloudCommandProperties.ZONE_DESCRIPTION)
  private String zone;

  @Parameter(
      names = "--instance-type",
      required = true,
      description = TencentCloudCommandProperties.INSTANCE_TYPE_DESCRIPTION)
  private String instanceType;

  @Parameter(
      names = "--source-image-id",
      required = true,
      description = TencentCloudCommandProperties.SOURCE_IMAGE_ID_DESCRIPTION)
  private String sourceImageId;

  @Parameter(
      names = "--ssh-user-name",
      required = true,
      description = TencentCloudCommandProperties.SSH_USER_NAME_DESCRIPTION)
  private String sshUserName;

  @Override
  protected BaseImage editBaseImage(TencentCloudBaseImage baseImage) {
    TencentCloudVirtualizationSettings virtualizationSettings =
        baseImage.getVirtualizationSettings().get(0);

    virtualizationSettings.setRegion(isSet(region) ? region : virtualizationSettings.getRegion());
    virtualizationSettings.setSourceImageId(
        isSet(sourceImageId) ? sourceImageId : virtualizationSettings.getSourceImageId());
    virtualizationSettings.setInstanceType(
        isSet(instanceType) ? instanceType : virtualizationSettings.getInstanceType());
    virtualizationSettings.setSshUserName(
        isSet(sshUserName) ? sshUserName : virtualizationSettings.getSshUserName());
    virtualizationSettings.setZone(isSet(zone) ? zone : virtualizationSettings.getZone());
    return baseImage;
  }
}
