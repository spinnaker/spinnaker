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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.api;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApiSecurity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class ApiSecurityCommand extends AbstractConfigCommand {
  private String commandName = "api";

  private String shortDescription =
      "Configure and view the API server's addressable URL and CORS policies.";

  public ApiSecurityCommand() {
    registerSubcommand(new ApiSecurityEditCommand());
    registerSubcommand(new SpringSslCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<ApiSecurity>()
        .setOperation(Daemon.getApiSecurity(currentDeployment, !noValidate))
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setFailureMesssage("Failed to load API security settings.")
        .setSuccessMessage("Successfully loaded API security settings.")
        .get();
  }
}
