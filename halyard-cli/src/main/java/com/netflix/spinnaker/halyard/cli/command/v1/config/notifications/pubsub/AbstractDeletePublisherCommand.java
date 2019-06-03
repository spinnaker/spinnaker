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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific PROVIDER publisher */
@Parameters(separators = "=")
public abstract class AbstractDeletePublisherCommand extends AbstractHasPublisherCommand {

  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  public String getShortDescription() {
    return "Delete a specific " + getPubsubName() + " publisher by name.";
  }

  @Override
  protected void executeThis() {
    deletePublisher(getPublisherName());
  }

  private void deletePublisher(String publisherName) {
    String currentDeployment = getCurrentDeployment();
    String pubsubName = getPubsubName();
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to delete publisher " + publisherName + " for pubsub " + pubsubName + ".")
        .setSuccessMessage(
            "Successfully deleted publisher " + publisherName + " for pubsub " + pubsubName + ".")
        .setOperation(
            Daemon.deletePublisher(currentDeployment, pubsubName, publisherName, !noValidate))
        .get();
  }
}
