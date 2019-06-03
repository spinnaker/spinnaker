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

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.STRING;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;

@Parameters(separators = "=")
public abstract class AbstractNamedHaServiceCommand extends AbstractHaServiceCommand {
  @Override
  public String getCommandName() {
    return getServiceName();
  }

  @Override
  protected String getShortDescription() {
    return "Manage and view Spinnaker configuration for the "
        + getServiceName()
        + " high availability service";
  }

  protected AbstractNamedHaServiceCommand() {
    registerSubcommand(
        new HaServiceEnableDisableCommandBuilder()
            .setServiceName(getServiceName())
            .setEnable(false)
            .build());

    registerSubcommand(
        new HaServiceEnableDisableCommandBuilder()
            .setServiceName(getServiceName())
            .setEnable(true)
            .build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String serviceName = getServiceName();
    new OperationHandler<HaService>()
        .setFailureMesssage("Failed to get high availability service " + serviceName + ".")
        .setSuccessMessage("Successfully got high availability service " + serviceName + ".")
        .setFormat(STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getHaService(currentDeployment, serviceName, !noValidate))
        .get();
  }
}
