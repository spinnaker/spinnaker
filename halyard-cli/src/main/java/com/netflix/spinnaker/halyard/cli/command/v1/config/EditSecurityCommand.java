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

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters()
public class EditSecurityCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Edit top-level spinnaker security settings.";

  @Parameter(
      names = "--ui-address",
      description = "The IP address the UI is to be served from. If this is anything other than localhost "
          + "it must be provisioned and owned by the cloud provider Spinnaker is being deployed to. The deployment "
          + "of Spinnaker will automatically provision a load balancer that binds to this address."
  )
  private String uiAddress;

  @Parameter(
      names = "--api-address",
      description = "The IP address the API is to be served from. If this is anything other than localhost "
          + "it must be provisioned and owned by the cloud provider Spinnaker is being deployed to. The deployment "
          + "of Spinnaker will automatically provision a load balancer that binds to this address."
  )
  private String apiAddress;

  @Parameter(
      names = "--ui-domain",
      description = "A domain that resolves to the address supplied to --ui-address. This is required for generating "
          + "valid SSL keypairs based on whatever CA you provide Spinnaker."
  )
  private String uiDomain;

  @Parameter(
      names = "--api-domain",
      description = "A domain that resolves to the address supplied to --api-address. This is required for generating "
          + "valid SSL keypairs based on whatever CA you provide Spinnaker."
  )
  private String apiDomain;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    Security security = new OperationHandler<Security>()
        .setOperation(Daemon.getSecurity(currentDeployment, false))
        .setFailureMesssage("Failed to load top-level security settings.")
        .get();

    int originalHash = security.hashCode();

    security.setUiAddress(uiAddress);
    security.setApiAddress(apiAddress);
    security.setUiDomain(uiDomain);
    security.setUiAddress(apiDomain);

    if (originalHash == security.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setSecurity(currentDeployment, !noValidate, security))
        .setSuccessMessage("Successfully updated security settings.")
        .setFailureMesssage("Failed to edit security settings.")
        .get();
  }
}
