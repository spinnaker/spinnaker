/*
 * Copyright 2020 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsService;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsTemplateEngine;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@ConditionalOnProperty("microsoftteams.enabled")
@Service
public class MicrosoftTeamsNotificationAgent extends AbstractEventNotificationAgent {

  private final MicrosoftTeamsService teamsService;
  private final MicrosoftTeamsTemplateEngine teamsTemplateEngine;

  @Autowired
  public MicrosoftTeamsNotificationAgent(
      MicrosoftTeamsService teamsService, MicrosoftTeamsTemplateEngine teamsTemplateEngine) {
    this.teamsService = teamsService;
    this.teamsTemplateEngine = teamsTemplateEngine;
  }

  @Override
  public String getNotificationType() {
    return "microsoftteams";
  }

  @Override
  public void sendNotifications(
      Map<String, Object> preference,
      String application,
      Event event,
      Map<String, String> config,
      String status) {
    log.info("Building Microsoft Teams notification");

    String configType = Optional.ofNullable(config).map(c -> (String) c.get("type")).orElse(null);
    String configLink = Optional.ofNullable(config).map(c -> (String) c.get("link")).orElse(null);
    Map context = Optional.ofNullable(event.content).map(e -> (Map) e.get("context")).orElse(null);

    String cardTitle =
        String.format(
            "%s %s for %s", WordUtils.capitalize(configType), status, application.toUpperCase());

    String eventName = "";
    String executionId =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("id"))
            .orElse(null);

    String executionUrl =
        String.format(
            "%s/#/applications/%s/%s/%s",
            getSpinnakerUrl(),
            application,
            configType == "stage" ? "executions/details" : configLink,
            executionId);

    String executionDescription =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("description"))
            .orElse(null);

    String executionName =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("name"))
            .orElse(null);

    String customMessage =
        Optional.ofNullable(preference)
            .map(p -> (Map) p.get("message"))
            .map(p -> (Map) p.get(configType + "." + status))
            .map(p -> (String) p.get("text"))
            .orElse(null);

    String summary;
    String eventNameLabel = "Event Name";

    if (configType == "stage") {
      eventName = Optional.ofNullable(event.content).map(e -> (String) e.get("name")).orElse(null);

      String stageName =
          Optional.ofNullable(context)
              .map(c -> (Map) c.get("stageDetails"))
              .map(c -> (String) c.get("name"))
              .orElse(null);

      eventName = eventName != null ? eventName : stageName;
      eventNameLabel = "Stage Name";
      summary =
          String.format("Stage %s for %s's %s pipeline ", eventName, application, executionName);
    } else if (configType == "pipeline") {
      eventNameLabel = "Pipeline Name";
      summary = String.format("%s's %s pipeline ", application, executionName);
    } else {
      summary = String.format("%s's %s task ", application, executionId);
    }

    summary +=
        (status.equalsIgnoreCase("starting") ? "is" : "has")
            + " "
            + (status.equalsIgnoreCase("complete") ? "completed successfully" : status);

    // Determine theme color based on status
    String themeColor = "0076D7"; // Default blue
    if (status != null) {
      String statusLower = status.toLowerCase();
      if (statusLower.contains("failed")) {
        themeColor = "EB1A1A"; // Red
      } else if (statusLower.contains("complete")) {
        themeColor = "73DB69"; // Green
      }
    }

    // Capitalize status for display
    String displayStatus =
        status != null ? status.substring(0, 1).toUpperCase() + status.substring(1) : null;

    // Build template context
    Map<String, Object> templateContext = new HashMap<>();
    templateContext.put("correlationId", UUID.randomUUID().toString());
    templateContext.put("summary", summary);
    templateContext.put("cardTitle", cardTitle);
    templateContext.put("themeColor", themeColor);
    templateContext.put("applicationName", application);
    templateContext.put("executionName", executionName);
    templateContext.put("executionUrl", executionUrl);
    templateContext.put("eventName", eventName);
    templateContext.put("eventNameLabel", eventNameLabel);
    templateContext.put("description", executionDescription);
    templateContext.put("customMessage", customMessage);
    templateContext.put("status", displayStatus);
    templateContext.put("spinnakerUrl", getSpinnakerUrl());
    templateContext.put("event", event);
    templateContext.put("configType", configType);

    log.info("Sending Microsoft Teams notification");
    String webhookUrl =
        Optional.ofNullable(preference).map(p -> (String) p.get("address")).orElse(null);

    try {
      String renderedMessage = teamsTemplateEngine.render("pipeline-notification", templateContext);
      ResponseBody response = teamsService.sendMessage(webhookUrl, renderedMessage);

      try {
        log.info(
            "Received response from Microsoft Teams Webhook for execution id {}. {}",
            executionId,
            response.string());
      } catch (IOException e) {
        log.info(
            "Received response from Microsoft Teams Webhook for execution id {} but failed to deserialize",
            executionId);
      }
    } catch (MicrosoftTeamsTemplateEngine.TemplateRenderException e) {
      log.error("Failed to render Microsoft Teams notification template", e);
      throw new RuntimeException("Failed to send Microsoft Teams notification", e);
    }
  }
}
