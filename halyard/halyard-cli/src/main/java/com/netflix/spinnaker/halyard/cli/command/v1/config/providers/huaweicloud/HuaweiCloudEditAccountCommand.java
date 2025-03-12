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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class HuaweiCloudEditAccountCommand extends AbstractEditAccountCommand<HuaweiCloudAccount> {
  protected String getProviderName() {
    return "huaweicloud";
  }

  @Parameter(
      names = "--account-type",
      description = HuaweiCloudCommandProperties.ACCOUNT_TYPE_DESCRIPTION)
  private String accountType;

  @Parameter(names = "--auth-url", description = HuaweiCloudCommandProperties.AUTH_URL_DESCRIPTION)
  private String authUrl;

  @Parameter(names = "--username", description = HuaweiCloudCommandProperties.USERNAME_DESCRIPTION)
  private String username;

  @Parameter(
      names = "--password",
      password = true,
      description = HuaweiCloudCommandProperties.PASSWORD_DESCRIPTION)
  private String password;

  @Parameter(
      names = "--project-name",
      description = HuaweiCloudCommandProperties.PROJECT_NAME_DESCRIPTION)
  private String projectName;

  @Parameter(
      names = "--domain-name",
      description = HuaweiCloudCommandProperties.DOMAIN_NAME_DESCRIPTION)
  private String domainName;

  @Parameter(
      names = "--regions",
      variableArity = true,
      description = HuaweiCloudCommandProperties.REGIONS_DESCRIPTION)
  private List<String> regions = new ArrayList<>();

  @Parameter(
      names = "--add-region",
      description = "Add this region to the list of managed regions.")
  private String addRegion;

  @Parameter(
      names = "--remove-region",
      description = "Remove this region from the list of managed regions.")
  private String removeRegion;

  @Parameter(names = "--insecure", description = HuaweiCloudCommandProperties.INSECURE_DESCRIPTION)
  private Boolean insecure;

  @Override
  protected Account editAccount(HuaweiCloudAccount account) {
    account.setAccountType(isSet(accountType) ? accountType : account.getAccountType());
    account.setAuthUrl(isSet(authUrl) ? authUrl : account.getAuthUrl());
    account.setUsername(isSet(username) ? username : account.getUsername());
    account.setPassword(isSet(password) ? password : account.getPassword());
    account.setProjectName(isSet(projectName) ? projectName : account.getProjectName());
    account.setDomainName(isSet(domainName) ? domainName : account.getDomainName());
    account.setInsecure(isSet(insecure) ? insecure : account.getInsecure());

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
