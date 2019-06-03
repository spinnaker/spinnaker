/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudfoundry;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryAccount;

@Parameters(separators = "=")
public class CloudFoundryEditAccountCommand
    extends AbstractEditAccountCommand<CloudFoundryAccount> {
  @Override
  protected String getProviderName() {
    return "cloudfoundry";
  }

  @Parameter(
      names = {"--api-host", "--api"},
      description = CloudFoundryCommandProperties.API_HOST_DESCRIPTION)
  private String apiHost;

  @Parameter(
      names = {"--apps-manager-uri", "--appsManagerUri"},
      description = CloudFoundryCommandProperties.APPS_MANAGER_URI_DESCRIPTION)
  private String appsManagerUri;

  @Parameter(
      names = {"--metrics-uri", "--metricsUri"},
      description = CloudFoundryCommandProperties.METRICS_URI_DESCRIPTION)
  private String metricsUri;

  @Parameter(names = "--password", description = CloudFoundryCommandProperties.PASSWORD_DESCRIPTION)
  private String password;

  @Parameter(names = "--user", description = CloudFoundryCommandProperties.USER_DESCRIPTION)
  private String user;

  @Override
  protected Account editAccount(CloudFoundryAccount account) {
    account.setApiHost(isSet(apiHost) ? apiHost : account.getApiHost());
    account.setAppsManagerUri(isSet(appsManagerUri) ? appsManagerUri : account.getAppsManagerUri());
    account.setMetricsUri(isSet(metricsUri) ? metricsUri : account.getMetricsUri());
    account.setPassword(isSet(password) ? password : account.getPassword());
    account.setUser(isSet(user) ? user : account.getUser());

    return account;
  }
}
