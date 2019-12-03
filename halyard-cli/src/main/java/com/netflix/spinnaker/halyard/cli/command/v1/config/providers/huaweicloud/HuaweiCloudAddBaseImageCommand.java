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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractAddBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBaseImage.HuaweiCloudVirtualizationSettings;
import java.util.ArrayList;

@Parameters(separators = "=")
public class HuaweiCloudAddBaseImageCommand extends AbstractAddBaseImageCommand {
  protected String getProviderName() {
    return "huaweicloud";
  }

  @Parameter(
      names = "--region",
      required = true,
      description = HuaweiCloudCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Parameter(
      names = "--instance-type",
      required = true,
      description = HuaweiCloudCommandProperties.INSTANCE_TYPE_DESCRIPTION)
  private String instanceType;

  @Parameter(
      names = "--source-image-id",
      required = true,
      description = HuaweiCloudCommandProperties.SOURCE_IMAGE_ID_DESCRIPTION)
  private String sourceImageId;

  @Parameter(
      names = "--ssh-user-name",
      required = true,
      description = HuaweiCloudCommandProperties.SSH_USER_NAME_DESCRIPTION)
  private String sshUserName;

  @Parameter(
      names = "--eip-type",
      required = true,
      description = HuaweiCloudCommandProperties.EIP_TYPE_DESCRIPTION)
  private String eipType;

  @Override
  protected BaseImage buildBaseImage(String baseImageId) {
    HuaweiCloudVirtualizationSettings virtualizationSettings =
        new HuaweiCloudVirtualizationSettings();

    virtualizationSettings.setSourceImageId(sourceImageId);
    virtualizationSettings.setRegion(region);
    virtualizationSettings.setInstanceType(instanceType);
    virtualizationSettings.setSshUserName(sshUserName);
    virtualizationSettings.setEipType(eipType);

    HuaweiCloudBaseImage baseImage = new HuaweiCloudBaseImage();
    baseImage.setBaseImage(new HuaweiCloudBaseImage.HuaweiCloudImageSettings());
    baseImage.setVirtualizationSettings(
        new ArrayList() {
          {
            add(virtualizationSettings);
          }
        });

    return baseImage;
  }
}
