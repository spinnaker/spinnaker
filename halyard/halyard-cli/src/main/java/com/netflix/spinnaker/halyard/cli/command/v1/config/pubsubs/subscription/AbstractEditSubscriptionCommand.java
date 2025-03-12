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
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Subscription;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditSubscriptionCommand<T extends Subscription>
    extends AbstractHasSubscriptionCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract Subscription editSubscription(T subscription);

  public String getShortDescription() {
    return "Edit an subscription in the " + getPubsubName() + " pubsub.";
  }

  @Override
  protected void executeThis() {
    String subscriptionName = getSubscriptionName();
    String pubsubName = getPubsubName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Subscription subscription =
        new OperationHandler<Subscription>()
            .setFailureMesssage(
                "Failed to get subscription "
                    + subscriptionName
                    + " for pubsub "
                    + pubsubName
                    + ".")
            .setOperation(
                Daemon.getSubscription(currentDeployment, pubsubName, subscriptionName, false))
            .get();

    int originaHash = subscription.hashCode();

    subscription = editSubscription((T) subscription);

    if (originaHash == subscription.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to edit subscription " + subscriptionName + " for pubsub " + pubsubName + ".")
        .setSuccessMessage(
            "Successfully edited subscription "
                + subscriptionName
                + " for pubsub "
                + pubsubName
                + ".")
        .setOperation(
            Daemon.setSubscription(
                currentDeployment, pubsubName, subscriptionName, !noValidate, subscription))
        .get();
  }
}
