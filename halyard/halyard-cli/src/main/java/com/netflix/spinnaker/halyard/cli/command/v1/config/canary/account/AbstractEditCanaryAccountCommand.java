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
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditCanaryAccountCommand<T extends AbstractCanaryAccount>
    extends AbstractHasCanaryAccountCommand {

  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract AbstractCanaryAccount editAccount(T account);

  @Getter(AccessLevel.PUBLIC)
  String shortDescription =
      "Edit a canary account in the " + getServiceIntegration() + " service integration.";

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    String serviceIntegration = getServiceIntegration();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    AbstractCanaryAccount account =
        new OperationHandler<AbstractCanaryAccount>()
            .setFailureMesssage(
                "Failed to get canary account "
                    + accountName
                    + " for service integration "
                    + serviceIntegration
                    + ".")
            .setOperation(
                Daemon.getCanaryAccount(
                    currentDeployment, serviceIntegration.toLowerCase(), accountName, false))
            .get();

    int originaHash = account.hashCode();

    account = editAccount((T) account);

    if (originaHash == account.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to edit canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setSuccessMessage(
            "Successfully edited canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setOperation(
            Daemon.setCanaryAccount(
                currentDeployment,
                serviceIntegration.toLowerCase(),
                accountName,
                !noValidate,
                account))
        .get();
  }
}
