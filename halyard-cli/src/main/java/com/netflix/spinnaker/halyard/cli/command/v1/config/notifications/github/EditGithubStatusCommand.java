/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.github;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.AbstractEditNotificationCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.GithubStatusNotification;

/** Interact with the github notification */
@Parameters(separators = "=")
public class EditGithubStatusCommand
    extends AbstractEditNotificationCommand<GithubStatusNotification> {
  protected String getNotificationName() {
    return "github-status";
  }

  @Parameter(names = "--token", password = true, description = "Your github account token.")
  private String token;

  @Override
  protected Notification editNotification(GithubStatusNotification notification) {
    notification.setToken(isSet(token) ? token : notification.getToken());
    return notification;
  }
}
