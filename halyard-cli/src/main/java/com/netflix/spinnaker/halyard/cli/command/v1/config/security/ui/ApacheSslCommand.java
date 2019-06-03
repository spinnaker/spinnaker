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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.ui;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApacheSsl;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class ApacheSslCommand extends AbstractConfigCommand {
  private String commandName = "ssl";

  private String shortDescription = "Configure and view SSL settings for Spinnaker's UI gateway.";

  private String longDescription =
      String.join(
          " ",
          "If you want the UI server to do SSL termination, it must be enabled and configured here.",
          "If you are doing your own SSL termination, leave this disabled.");

  public ApacheSslCommand() {
    registerSubcommand(new EnableDisableSslCommandBuilder().setEnable(true).build());
    registerSubcommand(new EnableDisableSslCommandBuilder().setEnable(false).build());
    registerSubcommand(new ApacheSslEditCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<ApacheSsl>()
        .setOperation(Daemon.getApacheSsl(currentDeployment, !noValidate))
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setFailureMesssage("Failed to load UI SSL settings.")
        .setSuccessMessage("Successfully loaded UI SSL settings.")
        .get();
  }
}
