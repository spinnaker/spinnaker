/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.pubsub;

import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Publisher;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

public abstract class AbstractAddPublisherCommand extends AbstractHasPublisherCommand {

  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  @Override
  public String getShortDescription() {
    return "Add a publisher of type " + getPubsubName();
  }

  protected abstract Publisher buildPublisher(String publisherName);

  @Override
  protected void executeThis() {
    String publisherName = getPublisherName();
    Publisher publisher = buildPublisher(publisherName);
    String pubsubName = getPubsubName();

    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to add notification publisher "
                + publisherName
                + " for pubsub system "
                + pubsubName
                + ".")
        .setSuccessMessage(
            "Successfully added notification publisher "
                + publisherName
                + " for pubsub system "
                + pubsubName
                + ".")
        .setOperation(Daemon.addPublisher(currentDeployment, pubsubName, !noValidate, publisher))
        .get();
  }
}
