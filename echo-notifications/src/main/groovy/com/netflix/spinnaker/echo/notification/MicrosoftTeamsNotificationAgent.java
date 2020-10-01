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
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsMessage;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsService;
import com.netflix.spinnaker.echo.microsoftteams.api.MicrosoftTeamsSection;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

@Slf4j
@ConditionalOnProperty("microsoftteams.enabled")
@Service
public class MicrosoftTeamsNotificationAgent extends AbstractEventNotificationAgent {

  private final MicrosoftTeamsService teamsService;

  @Autowired
  public MicrosoftTeamsNotificationAgent(MicrosoftTeamsService teamsService) {
    this.teamsService = teamsService;
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

    String message =
        Optional.ofNullable(preference)
            .map(p -> (Map) p.get("message"))
            .map(p -> (Map) p.get(configType + "." + status))
            .map(p -> (String) p.get("text"))
            .orElse(null);

    String summary;

    if (configType == "stage") {
      eventName = Optional.ofNullable(event.content).map(e -> (String) e.get("name")).orElse(null);

      String stageName =
          Optional.ofNullable(context)
              .map(c -> (Map) c.get("stageDetails"))
              .map(c -> (String) c.get("name"))
              .orElse(null);

      eventName = eventName != null ? eventName : stageName;
      summary =
          String.format("Stage %s for %s's %s pipeline ", eventName, application, executionName);
    } else if (configType == "pipeline") {
      summary = String.format("%s's %s pipeline ", application, executionName);
    } else {
      summary = String.format("%s's %s task ", application, executionId);
    }

    summary +=
        (status.equalsIgnoreCase("starting") ? "is" : "has")
            + " "
            + (status.equalsIgnoreCase("complete") ? "completed successfully" : status);

    MicrosoftTeamsMessage teamsMessage = new MicrosoftTeamsMessage(summary, status);
    MicrosoftTeamsSection section = teamsMessage.createSection(configType, cardTitle);

    section.setApplicationName(application);
    section.setDescription(executionDescription);
    section.setExecutionName(executionName);
    section.setEventName(eventName);
    section.setMessage(message);
    section.setStatus(status);
    section.setSummary(summary);
    section.setPotentialAction(executionUrl, null);

    teamsMessage.addSection(section);

    log.info("Sending Microsoft Teams notification");
    String baseUrl = "https://outlook.office.com/webhook/";
    String completeLink =
        Optional.ofNullable(preference).map(p -> (String) p.get("address")).orElse(null);

    String partialWebhookURL = completeLink.substring(baseUrl.length());
    Response response = teamsService.sendMessage(partialWebhookURL, teamsMessage);

    log.info(
        "Received response from Microsoft Teams Webhook  : {} {} for execution id {}. {}",
        response.getStatus(),
        response.getReason(),
        executionId,
        new String(((TypedByteArray) response.getBody()).getBytes()));
  }
}
