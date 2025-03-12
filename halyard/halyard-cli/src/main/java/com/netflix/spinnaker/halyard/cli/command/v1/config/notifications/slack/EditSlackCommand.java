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

package com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.slack;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.AbstractEditNotificationCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.SlackNotification;

/** Interact with the slack notification */
@Parameters(separators = "=")
public class EditSlackCommand extends AbstractEditNotificationCommand<SlackNotification> {
  protected String getNotificationName() {
    return "slack";
  }

  @Parameter(names = "--bot-name", description = "The name of your slack bot.")
  private String botName;

  @Parameter(names = "--token", password = true, description = "Your slack bot token.")
  private String token;

  @Parameter(
      names = "--base-url",
      description = "Slack endpoint. Optional, only set if using a compatible API.")
  private String baseUrl;

  @Parameter(
      names = "--force-use-incoming-webhook",
      description =
          "Force usage of incoming webhooks endpoint for slack. Optional, only set if using a compatible API.")
  private Boolean forceUseIncomingWebhook;

  @Override
  protected Notification editNotification(SlackNotification notification) {
    notification.setBaseUrl(isSet(baseUrl) ? baseUrl : notification.getBaseUrl());
    notification.setBotName(isSet(botName) ? botName : notification.getBotName());
    notification.setToken(isSet(token) ? token : notification.getToken());
    notification.setForceUseIncomingWebhook(
        isSet(forceUseIncomingWebhook)
            ? forceUseIncomingWebhook
            : notification.getForceUseIncomingWebhook());
    return notification;
  }
}
