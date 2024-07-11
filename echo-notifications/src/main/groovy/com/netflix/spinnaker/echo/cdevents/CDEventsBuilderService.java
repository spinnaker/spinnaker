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

package com.netflix.spinnaker.echo.cdevents;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.exceptions.FieldNotFoundException;
import dev.cdevents.constants.CDEventConstants.CDEventTypes;
import dev.cdevents.exception.CDEventsException;
import io.cloudevents.CloudEvent;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CDEventsBuilderService {

  public CloudEvent createCDEvent(
      Map<String, Object> preference,
      String application,
      Event event,
      Map<String, String> config,
      String status,
      String spinnakerUrl) {

    String configType =
        Optional.ofNullable(config)
            .map(c -> (String) c.get("type"))
            .orElseThrow(() -> new FieldNotFoundException("type"));
    String configLink =
        Optional.ofNullable(config)
            .map(c -> (String) c.get("link"))
            .orElseThrow(() -> new FieldNotFoundException("link"));

    String executionId =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("id"))
            .orElseThrow(() -> new FieldNotFoundException("execution.id"));

    String executionUrl =
        String.format(
            "%s/#/applications/%s/%s/%s",
            spinnakerUrl,
            application,
            configType == "stage" ? "executions/details" : configLink,
            executionId);

    String executionName =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("name"))
            .orElseThrow(() -> new FieldNotFoundException("execution.name"));

    String cdEventsType =
        Optional.ofNullable(preference)
            .map(p -> (String) p.get("cdEventsType"))
            .orElseThrow(() -> new FieldNotFoundException("notifications.cdEventsType"));

    Object customData =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("context"))
            .map(e -> e.get("customData"))
            .orElse(new Object());

    log.info("Event type {} received to create CDEvent.", cdEventsType);
    // This map will be updated to add more event types that Spinnaker needs to send
    Map<String, BaseCDEvent> cdEventsMap =
        Map.of(
            CDEventTypes.PipelineRunQueuedEvent.getEventType(),
                new CDEventPipelineRunQueued(
                    executionId, executionUrl, executionName, spinnakerUrl, customData),
            CDEventTypes.PipelineRunStartedEvent.getEventType(),
                new CDEventPipelineRunStarted(
                    executionId, executionUrl, executionName, spinnakerUrl, customData),
            CDEventTypes.PipelineRunFinishedEvent.getEventType(),
                new CDEventPipelineRunFinished(
                    executionId, executionUrl, executionName, spinnakerUrl, status, customData),
            CDEventTypes.TaskRunStartedEvent.getEventType(),
                new CDEventTaskRunStarted(
                    executionId, executionUrl, executionName, spinnakerUrl, customData),
            CDEventTypes.TaskRunFinishedEvent.getEventType(),
                new CDEventTaskRunFinished(
                    executionId, executionUrl, executionName, spinnakerUrl, status, customData));

    BaseCDEvent cdEvent =
        cdEventsMap.keySet().stream()
            .filter(keyType -> keyType.contains(cdEventsType))
            .map(cdEventsMap::get)
            .findFirst()
            .orElseThrow(
                () -> {
                  log.error("No mapping event type found for {}", cdEventsType);
                  log.error(
                      "The event type should be an event or substring of an event from the list of event types {}",
                      cdEventsMap.keySet());
                  return new CDEventsException("No mapping eventType found to create CDEvent");
                });

    return cdEvent.createCDEvent();
  }
}
