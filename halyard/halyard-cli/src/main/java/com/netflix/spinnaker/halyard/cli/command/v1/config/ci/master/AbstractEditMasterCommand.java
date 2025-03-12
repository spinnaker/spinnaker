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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditMasterCommand<T extends CIAccount>
    extends AbstractHasMasterCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Parameter(
      names = "--add-read-permission",
      description = "Add this permission to the list of read permissions.")
  private String addReadPermission;

  @Parameter(
      names = "--remove-read-permission",
      description = "Remove this permission from the list of read permissions.")
  private String removeReadPermission;

  @Parameter(
      variableArity = true,
      names = "--read-permissions",
      description = MasterCommandProperties.READ_PERMISSION_DESCRIPTION)
  private Set<String> readPermissions;

  @Parameter(
      names = "--add-write-permission",
      description = "Add this permission to the list of write permissions.")
  private String addWritePermission;

  @Parameter(
      names = "--remove-write-permission",
      description = "Remove this permission from the list of write permissions.")
  private String removeWritePermission;

  @Parameter(
      variableArity = true,
      names = "--write-permissions",
      description = MasterCommandProperties.WRITE_PERMISSION_DESCRIPTION)
  private Set<String> writePermissions;

  protected abstract CIAccount editMaster(T master);

  public String getShortDescription() {
    return "Edit a master for the " + getCiName() + " Continuous Integration service.";
  }

  @Override
  protected void executeThis() {
    String masterName = getMasterName();
    String ciName = getCiName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    CIAccount account =
        new OperationHandler<CIAccount>()
            .setOperation(Daemon.getMaster(currentDeployment, ciName, masterName, !noValidate))
            .setFailureMesssage("Failed to get " + masterName + " under " + ciName + ".")
            .get();

    int originalHash = account.hashCode();

    account = editMaster((T) account);

    updatePermissions(
        account.getPermissions(),
        readPermissions,
        addReadPermission,
        removeReadPermission,
        writePermissions,
        addWritePermission,
        removeWritePermission);

    if (originalHash == account.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setMaster(currentDeployment, ciName, masterName, !noValidate, account))
        .setSuccessMessage("Edited " + masterName + " for " + ciName + ".")
        .setFailureMesssage("Failed to edit " + masterName + " for " + ciName + ".")
        .get();
  }
}
