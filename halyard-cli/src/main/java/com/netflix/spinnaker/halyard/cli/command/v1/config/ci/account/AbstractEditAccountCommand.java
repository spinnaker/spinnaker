/*
 * Copyright 2020 Amazon, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditAccountCommand<T extends CIAccount>
    extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract CIAccount editAccount(T account);

  public String getShortDescription() {
    return "Edit a " + getCiFullName() + " account.";
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    String ciName = getCiName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    CIAccount account =
        new OperationHandler<CIAccount>()
            .setOperation(Daemon.getMaster(currentDeployment, ciName, accountName, false))
            .setFailureMesssage(
                String.format("Failed to edit %s account %s.", getCiFullName(), accountName))
            .get();

    int originalHash = account.hashCode();

    account = editAccount((T) account);

    if (originalHash == account.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(
            Daemon.setMaster(currentDeployment, ciName, accountName, !noValidate, account))
        .setSuccessMessage(String.format("Edited %s account %s.", getCiFullName(), accountName))
        .setFailureMesssage(
            String.format("Failed to edit %s account %s.", getCiFullName(), accountName))
        .get();
  }
}
