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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.datadog;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.EnableDisableCanaryServiceIntegrationCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.datadog.account.DatadogCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryServiceIntegration;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Parameters(separators = "=")
@Data
@EqualsAndHashCode(callSuper = false)
public class CanaryDatadogCommand extends AbstractConfigCommand {

  String commandName = DatadogCanaryServiceIntegration.NAME;

  String shortDescription =
      "Configure your canary analysis Datadog service integration settings for Spinnaker.";

  public CanaryDatadogCommand() {
    registerSubcommand(new EditCanaryDatadogCommand());
    registerSubcommand(
        new EnableDisableCanaryServiceIntegrationCommandBuilder()
            .setName("Datadog")
            .setEnable(true)
            .build());
    registerSubcommand(
        new EnableDisableCanaryServiceIntegrationCommandBuilder()
            .setName("Datadog")
            .setEnable(false)
            .build());
    registerSubcommand(new DatadogCanaryAccountCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<Canary>()
        .setOperation(Daemon.getCanary(currentDeployment, !noValidate))
        .setFailureMesssage("Failed to load canary settings.")
        .setSuccessMessage("Configured canary settings: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
