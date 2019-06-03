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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractHaServiceEditCommand<T extends HaService>
    extends AbstractHaServiceCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract HaService editHaService(T haService);

  public String getShortDescription() {
    return "Edit the " + getServiceName() + " high availability service";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String serviceName = getServiceName();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    HaService haService =
        new OperationHandler<HaService>()
            .setFailureMesssage("Failed to get high availability service " + serviceName + ".")
            .setOperation(Daemon.getHaService(currentDeployment, serviceName, false))
            .get();

    int originalHash = haService.hashCode();

    haService = editHaService((T) haService);

    if (originalHash == haService.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit high availability service " + serviceName + ".")
        .setSuccessMessage("Successfully edited high availability service " + serviceName + ".")
        .setOperation(Daemon.setHaService(currentDeployment, serviceName, !noValidate, haService))
        .get();
  }
}
