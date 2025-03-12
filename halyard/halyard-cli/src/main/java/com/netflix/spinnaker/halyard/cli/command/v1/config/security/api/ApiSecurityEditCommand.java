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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApiSecurity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class ApiSecurityEditCommand extends AbstractConfigCommand {
  private String commandName = "edit";

  private String shortDescription = "Configure access policies specific to Spinnaker's API server.";

  private String longDescription =
      String.join(
          " ",
          "When Spinnaker is deployed to a remote host, the API server may be configured",
          "to accept auth requests from alternate sources, do SSL termination, or sit behind",
          "an externally configured proxy server or load balancer.");

  @Parameter(
      names = "--override-base-url",
      description =
          "If you are accessing the API server remotely, provide the full base URL of whatever proxy or "
              + "load balancer is fronting the API requests.")
  String overrideBaseUrl;

  @Parameter(
      names = "--cors-access-pattern",
      description =
          "If you have authentication enabled, are accessing Spinnaker remotely, and are logging in from "
              + "sources other than the UI, provide a regex matching all URLs authentication redirects may come from.")
  String corsAccessPattern;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    ApiSecurity apiSecurity =
        new OperationHandler<ApiSecurity>()
            .setOperation(Daemon.getApiSecurity(currentDeployment, false))
            .setFailureMesssage("Failed to load API security settings.")
            .get();

    int originalHash = apiSecurity.hashCode();

    apiSecurity.setOverrideBaseUrl(
        isSet(overrideBaseUrl) ? overrideBaseUrl : apiSecurity.getOverrideBaseUrl());
    apiSecurity.setCorsAccessPattern(
        isSet(corsAccessPattern) ? corsAccessPattern : apiSecurity.getCorsAccessPattern());

    if (originalHash == apiSecurity.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setApiSecurity(currentDeployment, !noValidate, apiSecurity))
        .setFailureMesssage("Failed to edit API security settings.")
        .setFailureMesssage("Successfully updated API security settings.")
        .get();
  }
}
