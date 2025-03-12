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

package com.netflix.spinnaker.halyard.cli.command.v1.config.webhook;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class WebhookTrustEditCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's webhook trust configuration.";

  @Parameter(
      names = "--trustStore",
      converter = LocalFileConverter.class,
      description =
          "The path to a key store in JKS format containing certification authorities that should be trusted by webhook stages.")
  private String trustStore;

  @Parameter(
      names = "--trustStorePassword",
      password = true,
      description = "The password for the supplied trustStore.")
  private String trustStorePassword;

  public WebhookTrustEditCommand() {}

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    WebhookTrust webhookTrust =
        new OperationHandler<WebhookTrust>()
            .setOperation(Daemon.getWebhookTrust(currentDeployment, false))
            .setFailureMesssage("Failed to load webhook trust.")
            .get();

    int originalHash = webhookTrust.hashCode();

    if (isSet(trustStore)) {
      webhookTrust.setTrustStore(trustStore);
    }
    if (isSet(trustStorePassword)) {
      webhookTrust.setTrustStorePassword(trustStorePassword);
    }

    if (originalHash == webhookTrust.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit webhook trust.")
        .setSuccessMessage("Successfully edited webhook trust.")
        .setOperation(Daemon.setWebhookTrust(currentDeployment, !noValidate, webhookTrust))
        .get();
  }
}
