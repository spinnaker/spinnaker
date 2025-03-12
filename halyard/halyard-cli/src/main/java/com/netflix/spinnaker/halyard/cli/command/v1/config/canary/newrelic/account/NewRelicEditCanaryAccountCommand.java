/*
 * Copyright 2019 New Relic Corporation. All rights reserved.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.newrelic.account;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractEditCanaryAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.newrelic.NewRelicCanaryAccount;

@Parameters(separators = "=")
public class NewRelicEditCanaryAccountCommand
    extends AbstractEditCanaryAccountCommand<NewRelicCanaryAccount> {

  @Override
  protected String getServiceIntegration() {
    return "newrelic";
  }

  @Parameter(names = "--base-url", description = "The base URL to the New Relic Insights server.")
  private String baseUrl;

  @Parameter(
      names = "--api-key",
      password = true,
      description =
          "Your account's unique New Relic Insights API key. See https://docs.newrelic.com/docs/insights/insights-api/get-data/query-insights-event-data-api.")
  private String apiKey;

  @Parameter(
      names = "--application-key",
      description =
          "Your New Relic account id. See https://docs.newrelic.com/docs/accounts/install-new-relic/account-setup/account-id.")
  private String applicationKey;

  @Override
  protected AbstractCanaryAccount editAccount(NewRelicCanaryAccount account) {
    account.setEndpoint(
        isSet(baseUrl)
            ? new NewRelicCanaryAccount.Endpoint().setBaseUrl(baseUrl)
            : account.getEndpoint());
    account.setApiKey(isSet(apiKey) ? apiKey : account.getApiKey());
    account.setApplicationKey(isSet(applicationKey) ? applicationKey : account.getApplicationKey());

    return account;
  }
}
