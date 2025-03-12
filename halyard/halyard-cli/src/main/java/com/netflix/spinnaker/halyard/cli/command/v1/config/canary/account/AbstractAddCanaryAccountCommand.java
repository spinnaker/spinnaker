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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractAddCanaryAccountCommand extends AbstractHasCanaryAccountCommand {

  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  protected abstract AbstractCanaryAccount buildAccount(Canary canary, String accountName);

  protected abstract AbstractCanaryAccount emptyAccount();

  @Getter(AccessLevel.PUBLIC)
  String shortDescription =
      "Add a canary account to the " + getServiceIntegration() + " service integration.";

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Canary canary =
        new OperationHandler<Canary>()
            .setFailureMesssage("Failed to get canary.")
            .setOperation(Daemon.getCanary(currentDeployment, false))
            .get();

    String accountName = getAccountName();
    AbstractCanaryAccount account = buildAccount(canary, accountName);
    String serviceIntegration = getServiceIntegration();

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to add canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setSuccessMessage(
            "Successfully added canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setOperation(
            Daemon.addCanaryAccount(
                currentDeployment, serviceIntegration.toLowerCase(), !noValidate, account))
        .get();
  }
}
