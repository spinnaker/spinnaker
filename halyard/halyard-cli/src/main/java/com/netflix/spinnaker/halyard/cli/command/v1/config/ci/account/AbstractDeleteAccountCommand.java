/*
 * Copyright 2020 Amazon.com, Inc.
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
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific CI account */
@Parameters(separators = "=")
public abstract class AbstractDeleteAccountCommand extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  public String getShortDescription() {
    return "Delete a " + getCiFullName() + " account.";
  }

  @Override
  protected void executeThis() {
    deleteAccount(getAccountName());
    AnsiUi.success("Deleted " + getAccountName());
  }

  private void deleteAccount(String accountName) {
    String currentDeployment = getCurrentDeployment();
    String ciName = getCiName();
    new OperationHandler<Void>()
        .setOperation(Daemon.deleteMaster(currentDeployment, ciName, accountName, !noValidate))
        .setSuccessMessage(String.format("Deleted %s account %s.", getCiFullName(), accountName))
        .setFailureMesssage(
            String.format("Failed to delete %s account %s.", getCiFullName(), accountName))
        .get();
  }
}
