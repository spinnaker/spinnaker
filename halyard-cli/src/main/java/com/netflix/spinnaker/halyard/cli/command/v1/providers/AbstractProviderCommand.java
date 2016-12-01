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

package com.netflix.spinnaker.halyard.cli.command.v1.providers;

import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.DaemonService;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;

public abstract class AbstractProviderCommand extends NestableCommand {
  @Parameter(names = { "--no-validate" }, description = "Skip validation")
  public boolean noValidate = false;

  protected abstract String getProviderName();

  @Override
  public String getCommandName() {
    return getProviderName();
  }

  @Override
  public String getDescription() {
    return "Manage Spinnaker configuration for the " + getProviderName() + " provider";
  }

  protected AbstractProviderCommand() {
    String providerName = getProviderName();

    registerSubcommand(new GetAccountCommandBuilder()
        .setProviderName(providerName)
        .build()
    );

    registerSubcommand(new ProviderEnableDisableCommandBuilder()
        .setProviderName(providerName)
        .setEnable(false)
        .build()
    );

    registerSubcommand(new ProviderEnableDisableCommandBuilder()
        .setProviderName(providerName)
        .setEnable(false)
        .build()
    );
  }

  private Provider getProvider() {
    DaemonService service = Daemon.getService();
    String currentDeployment = service.getCurrentDeployment();
    ObjectMapper mapper = new ObjectMapper();
    String providerName = getProviderName();
    return mapper.convertValue(
        service.getProvider(currentDeployment, providerName, !noValidate),
        Providers.translateProviderType(providerName)
    );
  }

  @Override
  protected void executeThis() {
    AnsiUi.success(getProvider().toString());
  }
}
