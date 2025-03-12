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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.datadog.account;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractEditCanaryAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryAccount;

@Parameters(separators = "=")
public class DatadogEditCanaryAccountCommand
    extends AbstractEditCanaryAccountCommand<DatadogCanaryAccount> {

  @Override
  protected String getServiceIntegration() {
    return "Datadog";
  }

  @Parameter(names = "--base-url", description = "The base URL to the Datadog server.")
  private String baseUrl;

  @Parameter(
      names = "--api-key",
      password = true,
      description =
          "Your org's unique Datadog API key. See https://app.datadoghq.com/account/settings#api.")
  private String apiKey;

  @Parameter(
      names = "--application-key",
      password = true,
      description =
          "Your Datadog application key. See https://app.datadoghq.com/account/settings#api.")
  private String applicationKey;

  @Override
  protected AbstractCanaryAccount editAccount(DatadogCanaryAccount account) {
    account.setEndpoint(
        isSet(baseUrl)
            ? new DatadogCanaryAccount.Endpoint().setBaseUrl(baseUrl)
            : account.getEndpoint());
    account.setApiKey(isSet(apiKey) ? apiKey : account.getApiKey());
    account.setApplicationKey(isSet(applicationKey) ? applicationKey : account.getApplicationKey());

    return account;
  }
}
