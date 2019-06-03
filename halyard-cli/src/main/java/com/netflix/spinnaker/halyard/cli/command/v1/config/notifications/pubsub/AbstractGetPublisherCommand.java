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

import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.Publisher;
import lombok.Getter;

public abstract class AbstractGetPublisherCommand extends AbstractHasPublisherCommand {

  public String getShortDescription() {
    return "Get the specified publisher details for the " + getPubsubName() + " pubsub.";
  }

  @Getter private String commandName = "get";

  @Override
  protected void executeThis() {
    getPublisher(getPublisherName());
  }

  private Publisher getPublisher(String publisherName) {
    String currentDeployment = getCurrentDeployment();
    String pubsubName = getPubsubName();
    return new OperationHandler<Publisher>()
        .setFailureMesssage(
            "Failed to get publisher " + publisherName + " for pubsub " + pubsubName + ".")
        .setSuccessMessage("Publisher " + publisherName + ": ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getPublisher(currentDeployment, pubsubName, publisherName, false))
        .get();
  }
}
