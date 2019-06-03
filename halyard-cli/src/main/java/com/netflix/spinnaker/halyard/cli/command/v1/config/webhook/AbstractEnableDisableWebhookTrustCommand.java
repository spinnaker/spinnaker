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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEnableDisableWebhookTrustCommand extends AbstractConfigCommand {
  @Override
  public String getCommandName() {
    return isEnable() ? "enable" : "disable";
  }

  private String subjunctivePerfectAction() {
    return isEnable() ? "enabled" : "disabled";
  }

  private String indicativePastPerfectAction() {
    return isEnable() ? "enabled" : "disabled";
  }

  protected abstract boolean isEnable();

  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Override
  public String getShortDescription() {
    return "Set Spinnaker's webhook trust to " + subjunctivePerfectAction() + ".";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    boolean enable = isEnable();

    WebhookTrust webhookTrust =
        new OperationHandler<WebhookTrust>()
            .setOperation(Daemon.getWebhookTrust(currentDeployment, false))
            .setFailureMesssage("Failed to load webhook trust.")
            .get();

    int originalHash = webhookTrust.hashCode();

    webhookTrust.setEnabled(enable);

    if (originalHash == webhookTrust.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setSuccessMessage("Successfully " + indicativePastPerfectAction() + " webhook trust.")
        .setFailureMesssage("Failed to " + getCommandName() + " webhook trust.")
        .setOperation(Daemon.setWebhookTrust(currentDeployment, !noValidate, webhookTrust))
        .get();
  }
}
