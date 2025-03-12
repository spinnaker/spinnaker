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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.security.UiSecurity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class UiSecurityEditCommand extends AbstractConfigCommand {
  private String commandName = "edit";

  private String shortDescription = "Configure access policies specific to Spinnaker's UI server.";

  private String longDescription =
      String.join(
          " ",
          "When Spinnaker is deployed to a remote host, the UI server may be configured to",
          "do SSL termination, or sit behind an externally configured proxy server or load balancer.");

  @Parameter(
      names = "--override-base-url",
      description =
          "If you are accessing the UI server remotely, provide the full base URL of whatever proxy or "
              + "load balancer is fronting the UI requests.")
  String overrideBaseUrl;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    UiSecurity uiSecurity =
        new OperationHandler<UiSecurity>()
            .setOperation(Daemon.getUiSecurity(currentDeployment, false))
            .setFailureMesssage("Failed to load UI security settings.")
            .get();

    int originalHash = uiSecurity.hashCode();

    uiSecurity.setOverrideBaseUrl(
        isSet(overrideBaseUrl) ? overrideBaseUrl : uiSecurity.getOverrideBaseUrl());

    if (originalHash == uiSecurity.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setUiSecurity(currentDeployment, !noValidate, uiSecurity))
        .setFailureMesssage("Failed to edit UI security settings.")
        .setSuccessMessage("Successfully updated UI security settings.")
        .get();
  }
}
