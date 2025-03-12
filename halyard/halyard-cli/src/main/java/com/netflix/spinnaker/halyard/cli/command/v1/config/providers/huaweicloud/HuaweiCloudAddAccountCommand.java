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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class HuaweiCloudAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "huaweicloud";
  }

  @Parameter(
      names = "--account-type",
      description = HuaweiCloudCommandProperties.ACCOUNT_TYPE_DESCRIPTION)
  private String accountType;

  @Parameter(
      names = "--auth-url",
      required = true,
      description = HuaweiCloudCommandProperties.AUTH_URL_DESCRIPTION)
  private String authUrl;

  @Parameter(
      names = "--username",
      required = true,
      description = HuaweiCloudCommandProperties.USERNAME_DESCRIPTION)
  private String username;

  @Parameter(
      names = "--password",
      required = true,
      password = true,
      description = HuaweiCloudCommandProperties.PASSWORD_DESCRIPTION)
  private String password;

  @Parameter(
      names = "--project-name",
      required = true,
      description = HuaweiCloudCommandProperties.PROJECT_NAME_DESCRIPTION)
  private String projectName;

  @Parameter(
      names = "--domain-name",
      required = true,
      description = HuaweiCloudCommandProperties.DOMAIN_NAME_DESCRIPTION)
  private String domainName;

  @Parameter(
      names = "--regions",
      required = true,
      variableArity = true,
      description = HuaweiCloudCommandProperties.REGIONS_DESCRIPTION)
  private List<String> regions = new ArrayList<>();

  @Parameter(names = "--insecure", description = HuaweiCloudCommandProperties.INSECURE_DESCRIPTION)
  private boolean insecure;

  @Override
  protected Account buildAccount(String accountName) {
    HuaweiCloudAccount account = (HuaweiCloudAccount) new HuaweiCloudAccount().setName(accountName);
    account
        .setAccountType(accountType)
        .setAuthUrl(authUrl)
        .setUsername(username)
        .setPassword(password)
        .setProjectName(projectName)
        .setDomainName(domainName)
        .setInsecure(insecure)
        .setRegions(regions);

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new HuaweiCloudAccount();
  }
}
