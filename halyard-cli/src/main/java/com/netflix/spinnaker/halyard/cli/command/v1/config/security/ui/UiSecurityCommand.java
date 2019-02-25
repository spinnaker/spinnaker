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
import com.netflix.spinnaker.halyard.config.model.v1.security.UiSecurity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class UiSecurityCommand extends AbstractConfigCommand {
  private String commandName = "ui";

  private String shortDescription = "Configure and view the UI server's addressable URL.";

  public UiSecurityCommand() {
    registerSubcommand(new UiSecurityEditCommand());
    registerSubcommand(new ApacheSslCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<UiSecurity>()
        .setOperation(Daemon.getUiSecurity(currentDeployment, !noValidate))
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setFailureMesssage("Failed to load UI security settings.")
        .setSuccessMessage("Successfully loaded UI security settings.")
        .get();
  }
}
