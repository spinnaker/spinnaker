/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Parameters()
public abstract class AbstractEditAccountCommand<T extends Account> extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract Account editAccount(T account);

  public String getDescription() {
    return "Edit a " + getProviderName() + " account.";
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Account account = new OperationHandler<Account>()
        .setFailureMesssage("Failed to get account " + accountName + " for provider " + providerName + ".")
        .setOperation(Daemon.getAccount(currentDeployment, providerName, accountName, false))
        .get();

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit account " + accountName + " for provider " + providerName + ".")
        .setSuccessMessage("Successfully edited account " + accountName + " for provider " + providerName + ".")
        .setOperation(Daemon.setAccount(currentDeployment, providerName, accountName, !noValidate, editAccount((T) account)))
        .get();
  }
}
