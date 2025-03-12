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

package com.netflix.spinnaker.halyard.cli.command.v1.config.notifications;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notification;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditNotificationCommand<N extends Notification>
    extends AbstractNotificationCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract Notification editNotification(N notification);

  public String getShortDescription() {
    return "Edit the " + getNotificationName() + " notification type";
  }

  @Override
  protected void executeThis() {
    String notificationName = getNotificationName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Notification notification =
        new OperationHandler<Notification>()
            .setOperation(Daemon.getNotification(currentDeployment, notificationName, !noValidate))
            .setFailureMesssage("Failed to get " + notificationName + ".")
            .get();

    int originalHash = notification.hashCode();

    notification = editNotification((N) notification);

    if (originalHash == notification.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(
            Daemon.setNotification(currentDeployment, notificationName, !noValidate, notification))
        .setSuccessMessage("Edited " + notificationName + ".")
        .setFailureMesssage("Failed to edit " + notificationName + ".")
        .get();
  }
}
