/*
 * Copyright 2019 New Relic Corporation. All rights reserved.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.newrelic;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.EnableDisableCanaryServiceIntegrationCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.newrelic.account.NewRelicCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.newrelic.NewRelicCanaryServiceIntegration;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Parameters(separators = "=")
@Data
@EqualsAndHashCode(callSuper = false)
public class CanaryNewRelicCommand extends AbstractConfigCommand {

  String commandName = NewRelicCanaryServiceIntegration.NAME;

  String shortDescription =
      "Configure your canary analysis New Relic service integration settings for Spinnaker.";

  public CanaryNewRelicCommand() {
    registerSubcommand(
        new EnableDisableCanaryServiceIntegrationCommandBuilder()
            .setName("newrelic")
            .setEnable(true)
            .build());
    registerSubcommand(
        new EnableDisableCanaryServiceIntegrationCommandBuilder()
            .setName("newrelic")
            .setEnable(false)
            .build());
    registerSubcommand(new NewRelicCanaryAccountCommand());
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
