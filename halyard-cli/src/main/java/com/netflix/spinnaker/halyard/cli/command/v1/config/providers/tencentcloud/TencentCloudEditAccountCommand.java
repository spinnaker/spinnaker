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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudAccount;
import java.util.List;

public class TencentCloudEditAccountCommand
    extends AbstractEditAccountCommand<TencentCloudAccount> {
  @Override
  protected String getProviderName() {
    return "tencentcloud";
  }

  @Parameter(
      names = "--secret-id",
      description = TencentCloudCommandProperties.SECRET_ID_DESCRIPTION)
  private String secretId;

  @Parameter(
      names = "--secret-key",
      password = true,
      description = TencentCloudCommandProperties.SECRET_KEY_DESCRIPTION)
  private String secretKey;

  @Parameter(
      names = "--regions",
      variableArity = true,
      description = TencentCloudCommandProperties.REGIONS_DESCRIPTION)
  private List<String> regions;

  @Parameter(
      names = "--add-region",
      description = TencentCloudCommandProperties.ADD_REGION_DESCRIPTION)
  private String addRegion;

  @Parameter(
      names = "--remove-region",
      description = TencentCloudCommandProperties.REMOVE_REGION_DESCRIPTION)
  private String removeRegion;

  @Override
  protected Account editAccount(TencentCloudAccount account) {
    account.setSecretId(isSet(secretId) ? secretId : account.getSecretId());
    account.setSecretKey(isSet(secretKey) ? secretKey : account.getSecretKey());

    try {
      List<String> existingRegions = account.getRegions();
      List<String> newRegions = updateStringList(existingRegions, regions, addRegion, removeRegion);
      account.setRegions(newRegions);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --regions or --[add/remove]-region");
    }

    return account;
  }
}
