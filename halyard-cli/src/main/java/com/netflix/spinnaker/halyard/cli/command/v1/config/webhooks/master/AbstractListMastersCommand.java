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

package com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.master;

import com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.AbstractWebhookCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Master;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhook;
import lombok.Getter;

import java.util.List;

abstract class AbstractListMastersCommand extends AbstractWebhookCommand {
  public String getDescription() {
    return "List the master names for the " + getWebhookName() + " webhook.";
  }

  @Getter
  private String commandName = "list";

  private Webhook getWebhook() {
    String currentDeployment = Daemon.getCurrentDeployment();
    return Daemon.getWebhook(currentDeployment, getWebhookName(), !noValidate);
  }

  @Override
  protected void executeThis() {
    Webhook webhook = getWebhook();
    List<Master> masters = webhook.getMasters();
    if (masters.isEmpty()) {
      AnsiUi.success("No configured masters for " + getWebhookName() + ".");
    } else {
      AnsiUi.success("Masters for " + getWebhookName() + ":");
      masters.forEach(master -> AnsiUi.listItem(master.getName()));
    }
  }
}
