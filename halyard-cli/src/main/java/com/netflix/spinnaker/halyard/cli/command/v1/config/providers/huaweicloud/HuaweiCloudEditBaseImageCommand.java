/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.huaweicloud;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBaseImage.HuaweiCloudVirtualizationSettings;

@Parameters(separators = "=")
public class HuaweiCloudEditBaseImageCommand
    extends AbstractEditBaseImageCommand<HuaweiCloudBaseImage> {
  @Override
  protected String getProviderName() {
    return "huaweicloud";
  }

  @Parameter(names = "--region", description = HuaweiCloudCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Parameter(
      names = "--instance-type",
      description = HuaweiCloudCommandProperties.INSTANCE_TYPE_DESCRIPTION)
  private String instanceType;

  @Parameter(
      names = "--source-image-id",
      description = HuaweiCloudCommandProperties.SOURCE_IMAGE_ID_DESCRIPTION)
  private String sourceImageId;

  @Parameter(
      names = "--ssh-user-name",
      description = HuaweiCloudCommandProperties.SSH_USER_NAME_DESCRIPTION)
  private String sshUserName;

  @Parameter(names = "--eip-type", description = HuaweiCloudCommandProperties.EIP_TYPE_DESCRIPTION)
  private String eipType;

  @Override
  protected BaseImage editBaseImage(HuaweiCloudBaseImage baseImage) {
    HuaweiCloudVirtualizationSettings virtualizationSettings =
        baseImage.getVirtualizationSettings().get(0);

    virtualizationSettings.setRegion(isSet(region) ? region : virtualizationSettings.getRegion());
    virtualizationSettings.setSourceImageId(
        isSet(sourceImageId) ? sourceImageId : virtualizationSettings.getSourceImageId());
    virtualizationSettings.setInstanceType(
        isSet(instanceType) ? instanceType : virtualizationSettings.getInstanceType());
    virtualizationSettings.setSshUserName(
        isSet(sshUserName) ? sshUserName : virtualizationSettings.getSshUserName());
    virtualizationSettings.setEipType(
        isSet(eipType) ? eipType : virtualizationSettings.getEipType());
    return baseImage;
  }
}
