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

import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RolesCommand extends AbstractConfigCommand {

  public RolesCommand() {
    super();
    registerSubcommand(new RolesProviderCommand());

    registerSubcommand(new EditRolesCommand());

    registerSubcommand(new EnableDisableRolesCommandBuilder()
        .setEnable(true)
        .build()
    );

    registerSubcommand(new EnableDisableRolesCommandBuilder()
        .setEnable(false)
        .build()
    );
  }

  private String commandName = "roles";
  private String description = "Configure authorization via a roles provider.";

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<GroupMembership>()
        .setOperation(Daemon.getGroupMembership(currentDeployment, true))
        .setFailureMesssage("Failed to get configured roles.")
        .setSuccessMessage("Configured roles: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .get();
  }
}
