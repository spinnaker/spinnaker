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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;

@Parameters(separators = "=")
public abstract class AbstractRoleProviderCommand extends AbstractConfigCommand {
  public abstract GroupMembership.RoleProviderType getRoleProviderType();

  @Override
  public String getShortDescription() {
    return "Configure the " + getRoleProviderType() + " role provider.";
  }

  @Override
  public String getCommandName() {
    return getRoleProviderType() + "";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    new OperationHandler<RoleProvider>()
        .setOperation(Daemon.getRoleProvider(currentDeployment, getRoleProviderType() + "", true))
        .setFailureMesssage("Failed to get " + getRoleProviderType() + " configuration.")
        .setSuccessMessage("Configured " + getRoleProviderType() + " role provider:")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
