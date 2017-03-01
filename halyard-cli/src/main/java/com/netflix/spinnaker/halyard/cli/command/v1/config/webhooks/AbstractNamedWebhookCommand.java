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

package com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks;

import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhook;

public abstract class AbstractNamedWebhookCommand extends AbstractWebhookCommand {
  @Override
  public String getCommandName() {
    return getWebhookName();
  }

  @Override
  public String getDescription() {
    return "Manage and view Spinnaker configuration for the " + getWebhookName() + " webhook";
  }

  protected AbstractNamedWebhookCommand() {
    registerSubcommand(new WebhookEnableDisableCommandBuilder()
        .setWebhookName(getWebhookName())
        .setEnable(false)
        .build()
    );

    registerSubcommand(new WebhookEnableDisableCommandBuilder()
        .setWebhookName(getWebhookName())
        .setEnable(true)
        .build()
    );
  }

  private Webhook getWebhook() {
    return Daemon.getWebhook(getCurrentDeployment(), getWebhookName(), !noValidate);
  }

  @Override
  protected void executeThis() {
    AnsiUi.success(getWebhook().toString());
  }
}
