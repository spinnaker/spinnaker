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
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditRoleProviderCommand<T extends RoleProvider>
    extends AbstractRoleProviderCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract RoleProvider editRoleProvider(T roleProvider);

  public String getShortDescription() {
    return "Edit the " + getRoleProviderType() + " role provider.";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String roleProviderName = getRoleProviderType() + "";
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    RoleProvider roleProvider =
        new OperationHandler<RoleProvider>()
            .setOperation(Daemon.getRoleProvider(currentDeployment, roleProviderName, false))
            .setFailureMesssage("Failed to get " + roleProviderName + " method.")
            .get();

    new OperationHandler<Void>()
        .setOperation(
            Daemon.setRoleProvider(
                currentDeployment,
                roleProviderName,
                !noValidate,
                editRoleProvider((T) roleProvider)))
        .setFailureMesssage("Failed to edit " + roleProviderName + " method.")
        .setSuccessMessage("Successfully edited " + roleProviderName + " method.")
        .get();
  }
}
