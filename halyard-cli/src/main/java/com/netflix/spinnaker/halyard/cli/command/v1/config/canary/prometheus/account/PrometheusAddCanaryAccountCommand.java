/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.prometheus.account;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractAddCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryAccount;

@Parameters(separators = "=")
public class PrometheusAddCanaryAccountCommand extends AbstractAddCanaryAccountCommand {

  @Override
  protected String getServiceIntegration() {
    return "Prometheus";
  }

  @Parameter(
      names = "--base-url",
      required = true,
      description = "The base URL to the Prometheus server.")
  private String baseUrl;

  @Parameter(names = "--username", description = "A basic auth username.")
  private String username;

  @Parameter(names = "--password", description = "A basic auth password.", password = true)
  private String password;

  @Parameter(
      names = "--username-password-file",
      converter = LocalFileConverter.class,
      description = "The path to a file containing \"username:password\".")
  private String usernamePasswordFile;

  @Override
  protected AbstractCanaryAccount buildAccount(Canary canary, String accountName) {
    PrometheusCanaryAccount account =
        (PrometheusCanaryAccount) new PrometheusCanaryAccount().setName(accountName);

    account.setEndpoint(new PrometheusCanaryAccount.Endpoint().setBaseUrl(baseUrl));
    account
        .setUsername(username)
        .setPassword(password)
        .setUsernamePasswordFile(usernamePasswordFile);

    return account;
  }

  @Override
  protected AbstractCanaryAccount emptyAccount() {
    return new PrometheusCanaryAccount();
  }
}
