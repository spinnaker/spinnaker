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

import com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.master.AbstractHasMasterCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.master.DeleteMasterCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.master.GetMasterCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.master.ListMastersCommandBuilder;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.config.model.v1.node.Master;

import static com.netflix.spinnaker.halyard.cli.services.v1.Daemon.getCurrentDeployment;

public abstract class AbstractMasterCommand extends AbstractHasMasterCommand {
  @Override
  public String getCommandName() {
    return "master";
  }

  @Override
  public String getDescription() {
    return "Manage and view Spinnaker configuration for the " + getWebhookName() + " webhook's master";
  }

  protected AbstractMasterCommand() {
    registerSubcommand(new DeleteMasterCommandBuilder()
        .setWebhookName(getWebhookName())
        .build()
    );

    registerSubcommand(new GetMasterCommandBuilder()
        .setWebhookName(getWebhookName())
        .build()
    );

    registerSubcommand(new ListMastersCommandBuilder()
        .setWebhookName(getWebhookName())
        .build()
    );
  }

  @Override
  protected void executeThis() {
    showHelp();
  }

  private Master getMaster(String masterName) {
    String currentDeployment = getCurrentDeployment();
    return Daemon.getMaster(currentDeployment, getWebhookName(), masterName, !noValidate);
  }
}
