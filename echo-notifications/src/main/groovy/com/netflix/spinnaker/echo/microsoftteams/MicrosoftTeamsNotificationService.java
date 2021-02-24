/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.microsoftteams;

import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.controller.EchoResponse;
import com.netflix.spinnaker.echo.microsoftteams.api.MicrosoftTeamsSection;
import com.netflix.spinnaker.echo.notification.NotificationService;
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("microsoftteams.enabled")
@Slf4j
class MicrosoftTeamsNotificationService implements NotificationService {

  private final MicrosoftTeamsService teamsService;
  private final NotificationTemplateEngine notificationTemplateEngine;

  @Value("${spinnaker.base-url}")
  private String spinnakerUrl;

  @Autowired
  public MicrosoftTeamsNotificationService(
      MicrosoftTeamsService service, NotificationTemplateEngine engine) {
    this.teamsService = service;
    this.notificationTemplateEngine = engine;
  }

  @Override
  public boolean supportsType(String type) {
    return "MICROSOFTTEAMS".equals(type.toUpperCase());
  }

  @Override
  public EchoResponse.Void handle(Notification notification) {
    log.info("Building Microsoft Teams event notification");

    String link =
        spinnakerUrl
            + "/#/applications/"
            + notification.getSource().getApplication()
            + "/executions/details/"
            + notification.getSource().getExecutionId();

    String message =
        notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.SUBJECT);
    String summary =
        notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY);

    MicrosoftTeamsMessage teamsMessage = new MicrosoftTeamsMessage(summary, null);
    MicrosoftTeamsSection section = teamsMessage.createSection(null, "Event Notification");

    section.setMessage(message);
    section.setSummary(summary);

    if (notification.getAdditionalContext().get("stageId") != null) {
      link += "?refId=" + notification.getAdditionalContext().get("stageId");

      if (notification.getAdditionalContext().get("restrictExecutionDuringTimeWindow") != null) {
        link += "&step=1";
      }
    }

    if (notification.isInteractive()) {
      log.info("Notification is interactive");
      section.setPotentialAction(null, notification.getInteractiveActions());
    } else {
      section.setPotentialAction(link, null);
    }

    teamsMessage.addSection(section);

    for (String webhookUrl : notification.getTo()) {
      log.info("Sending Microsoft Teams event notification");
      log.debug("Teams Webhook URL: {}", webhookUrl);

      teamsService.sendMessage(webhookUrl, teamsMessage);
    }

    return new EchoResponse.Void();
  }
}
