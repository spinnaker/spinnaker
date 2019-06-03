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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.subscription;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.AbstractPubsubCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsub;
import com.netflix.spinnaker.halyard.config.model.v1.node.Subscription;
import java.util.List;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractListSubscriptionsCommand extends AbstractPubsubCommand {
  public String getShortDescription() {
    return "List the subscription names for the " + getPubsubName() + " pubsub.";
  }

  @Getter private String commandName = "list";

  private Pubsub getPubsub() {
    String currentDeployment = getCurrentDeployment();
    String pubsubName = getPubsubName();
    return new OperationHandler<Pubsub>()
        .setFailureMesssage("Failed to get pubsub " + pubsubName + ".")
        .setOperation(Daemon.getPubsub(currentDeployment, pubsubName, !noValidate))
        .get();
  }

  @Override
  protected void executeThis() {
    Pubsub pubsub = getPubsub();
    List<Subscription> subscriptions = pubsub.getSubscriptions();
    if (subscriptions.isEmpty()) {
      AnsiUi.success("No configured subscriptions for " + getPubsubName() + ".");
    } else {
      AnsiUi.success("Subscriptions for " + getPubsubName() + ":");
      subscriptions.forEach(subscription -> AnsiUi.listItem(subscription.getName()));
    }
  }
}
