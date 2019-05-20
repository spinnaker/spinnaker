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
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.AbstractHasAccountCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.ci.gcb.GoogleCloudBuildAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Parameters(separators = "=")
public class GoogleCloudBuildAddAccountCommand extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  protected String getCiName() {
    return "gcb";
  }

  public String getShortDescription() {
    return "Add a Google Cloud Build account";
  }

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  @Parameter(
      names = "--project",
      required = true,
      description = "The name of the GCP project in which to trigger and monitor builds"
  )
  private String project;

  @Parameter(
      names = "--subscription-name",
      description = "The name of the PubSub subscription on which to listen for build changes"
  )
  private String subscriptionName;

  @Parameter(
      names = "--json-key",
      description = "The path to a JSON service account that Spinnaker will use as credentials"
  )
  private String jsonKey;

  protected GoogleCloudBuildAccount buildAccount(String accountName) {
    return new GoogleCloudBuildAccount()
        .setName(accountName)
        .setProject(project)
        .setSubscriptionName(subscriptionName)
        .setJsonKey(jsonKey);
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    CIAccount account = buildAccount(accountName);
    String ciName = getCiName();

    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Void>()
        .setOperation(Daemon.addMaster(currentDeployment, ciName, !noValidate, account))
        .setSuccessMessage(String.format("Added Google Cloud Build account %s.", accountName))
        .setFailureMesssage(String.format("Failed to add Google Cloud Build account %s.", accountName))
        .get();
  }
}
