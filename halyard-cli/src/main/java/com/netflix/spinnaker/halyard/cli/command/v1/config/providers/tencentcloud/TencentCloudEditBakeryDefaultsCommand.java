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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.BakeryCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudBakeryDefaults;

@Parameters(separators = "=")
public class TencentCloudEditBakeryDefaultsCommand
    extends AbstractEditBakeryDefaultsCommand<TencentCloudBakeryDefaults> {

  @Override
  protected String getProviderName() {
    return "tencentcloud";
  }

  @Parameter(
      names = "--secret-id",
      required = true,
      description = "The default access key used to communicate with AWS.")
  private String secretId;

  @Parameter(
      names = "--secret-key",
      required = true,
      description = "The secret key used to communicate with AWS.",
      password = true)
  private String secretKey;

  @Parameter(
      names = "--template-file",
      description = BakeryCommandProperties.TEMPLATE_FILE_DESCRIPTION)
  private String templateFile;

  @Override
  protected BakeryDefaults editBakeryDefaults(TencentCloudBakeryDefaults bakeryDefaults) {
    bakeryDefaults.setSecretId(isSet(secretId) ? secretId : bakeryDefaults.getSecretId());
    bakeryDefaults.setSecretKey(isSet(secretId) ? secretKey : bakeryDefaults.getSecretId());
    bakeryDefaults.setTemplateFile(
        isSet(templateFile) ? templateFile : bakeryDefaults.getTemplateFile());

    return bakeryDefaults;
  }
}
