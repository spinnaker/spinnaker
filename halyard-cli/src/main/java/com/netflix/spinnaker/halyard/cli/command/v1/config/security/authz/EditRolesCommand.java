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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EditRolesCommand extends AbstractConfigCommand {
  private String commandName = "edit";
  private String description = "Edit your roles provider settings.";

  @Parameter(
      names = "--type",
      description = "Set a roles provider type"
  )
  GroupMembership.RoleProviderType type;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    GroupMembership membership = new OperationHandler<GroupMembership>()
        .setOperation(Daemon.getGroupMembership(currentDeployment, false))
        .setFailureMesssage("Failed to get configured roles.")
        .get();

    int originalHash = membership.hashCode();

    membership.setRoleProviderType(type != null ? type : membership.getRoleProviderType());

    if (originalHash == membership.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }
     new OperationHandler<Void>()
        .setOperation(Daemon.setGroupMembership(currentDeployment, !noValidate, membership))
        .setFailureMesssage("Failed to set configured roles.")
        .setSuccessMessage("Successfully updated roles.")
        .get();
  }
}
