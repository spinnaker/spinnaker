/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers;

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.STRING;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;

@Parameters(separators = "=")
public abstract class AbstractNamedProviderCommand extends AbstractProviderCommand {
  @Override
  public String getCommandName() {
    return getProviderName();
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the " + getProviderName() + " provider";
  }

  protected AbstractNamedProviderCommand() {
    registerSubcommand(
        new ProviderEnableDisableCommandBuilder()
            .setProviderName(getProviderName())
            .setEnable(false)
            .build());

    registerSubcommand(
        new ProviderEnableDisableCommandBuilder()
            .setProviderName(getProviderName())
            .setEnable(true)
            .build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String providerName = getProviderName();
    new OperationHandler<Provider>()
        .setFailureMesssage("Failed to get provider " + providerName + ".")
        .setSuccessMessage("Successfully got provider " + providerName + ".")
        .setFormat(STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
        .get();
  }
}
