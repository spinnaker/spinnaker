/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.gcb;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.ci.gcb.GoogleCloudBuildAccount;

@Parameters(separators = "=")
public class GoogleCloudBuildEditAccountCommand
    extends AbstractEditAccountCommand<GoogleCloudBuildAccount> {
  protected String getCiName() {
    return "gcb";
  }

  @Override
  protected String getCiFullName() {
    return "Google Cloud Build";
  }

  @Parameter(
      names = "--project",
      description = GoogleCloudBuildCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--subscription-name",
      description = GoogleCloudBuildCommandProperties.SUBSCRIPTION_NAME_DESCRIPTION)
  public String subscriptionName;

  @Parameter(
      names = "--json-key",
      description = GoogleCloudBuildCommandProperties.JSON_KEY_DESCRIPTION)
  public String jsonKey;

  @Override
  protected GoogleCloudBuildAccount editAccount(GoogleCloudBuildAccount account) {
    if (isSet(project)) {
      account.setProject(project);
    }

    if (isSet(subscriptionName)) {
      account.setSubscriptionName(subscriptionName);
    }

    if (isSet(jsonKey)) {
      account.setJsonKey(jsonKey);
    }

    return account;
  }
}
