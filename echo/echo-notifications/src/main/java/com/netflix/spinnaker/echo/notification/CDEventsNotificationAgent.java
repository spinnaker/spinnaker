/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.cdevents.CDEventsBuilderService;
import com.netflix.spinnaker.echo.cdevents.CDEventsSenderService;
import com.netflix.spinnaker.echo.exceptions.FieldNotFoundException;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import retrofit2.Response;

@Slf4j
@ConditionalOnProperty("cdevents.enabled")
@Service
public class CDEventsNotificationAgent extends AbstractEventNotificationAgent {
  @Autowired CDEventsBuilderService cdEventsBuilderService;
  @Autowired CDEventsSenderService cdEventsSenderService;

  @Override
  public String getNotificationType() {
    return "cdevents";
  }

  @Override
  public void sendNotifications(
      Map<String, Object> preference,
      String application,
      Event event,
      Map<String, String> config,
      String status) {
    log.info("Sending CDEvents notification..");

    String executionId =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("id"))
            .orElseThrow(() -> new FieldNotFoundException("execution.id"));
    String cdEventsType =
        Optional.ofNullable(preference)
            .map(p -> (String) p.get("cdEventsType"))
            .orElseThrow(() -> new FieldNotFoundException("notifications.cdEventsType"));
    String eventsBrokerUrl =
        Optional.ofNullable(preference)
            .map(p -> (String) p.get("address"))
            .orElseThrow(() -> new FieldNotFoundException("notifications.address"));

    CloudEvent cdEvent =
        cdEventsBuilderService.createCDEvent(
            preference, application, event, config, status, getSpinnakerUrl());
    log.info(
        "Sending CDEvent {} notification to events broker url {}", cdEventsType, eventsBrokerUrl);
    Response<ResponseBody> response = cdEventsSenderService.sendCDEvent(cdEvent, eventsBrokerUrl);
    if (response != null) {
      try {
        log.info(
            "Received response from events broker : {} {} for execution id {}. {}",
            response.code(),
            response.message(),
            executionId,
            response.body() != null ? response.body().string() : "");
      } catch (IOException e) {
        log.info(
            "Received response from events broker : {} {} for execution id {} "
                + "but unable to serialize the response body: {}",
            response.code(),
            response.message(),
            executionId,
            e.getMessage());
      }
    }
  }
}
