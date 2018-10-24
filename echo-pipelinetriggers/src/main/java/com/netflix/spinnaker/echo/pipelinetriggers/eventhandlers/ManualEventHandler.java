/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link ManualEvent}, which occur when a
 * user manually requests a particular pipeline to execute (possibly supplying parameters to include
 * in the trigger). This event handler is unique in that the trigger uniquely specifies which pipeline
 * to execute; rather than looking at pipeline triggers for one that matches the event, it simply
 * looks for the pipeline whose application and id/name match the manual execution request.
 */
@Component
public class ManualEventHandler implements TriggerEventHandler<ManualEvent> {
  private static final String MANUAL_TRIGGER_TYPE = "manual";

  private final ObjectMapper objectMapper;

  @Autowired
  public ManualEventHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(ManualEvent.TYPE);
  }

  @Override
  public ManualEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, ManualEvent.class);
  }

  @Override
  public Optional<Pipeline> withMatchingTrigger(ManualEvent manualEvent, Pipeline pipeline) {
    Content content = manualEvent.getContent();
    String application = content.getApplication();
    String pipelineNameOrId = content.getPipelineNameOrId();
    if (pipelineMatches(application, pipelineNameOrId, pipeline)) {
      return Optional.of(buildTrigger(pipeline, content.getTrigger()));
    } else {
      return Optional.empty();
    }
  }

  private boolean pipelineMatches(String application, String nameOrId, Pipeline pipeline) {
    return !pipeline.isDisabled()
      && pipeline.getApplication().equals(application)
      && (pipeline.getName().equals(nameOrId) || pipeline.getId().equals(nameOrId));
  }

  private Pipeline buildTrigger(Pipeline pipeline, Trigger manualTrigger) {
    List<Map<String, Object>> notifications = buildNotifications(pipeline.getNotifications(), manualTrigger.getNotifications());
    return pipeline
      .withTrigger(manualTrigger.atPropagateAuth(true))
      .withNotifications(notifications);
  }

  private List<Map<String, Object>> buildNotifications(List<Map<String, Object>> pipelineNotifications, List<Map<String, Object>> triggerNotifications) {
    List<Map<String, Object>> notifications = new ArrayList<>();
    if (pipelineNotifications != null) {
      notifications.addAll(pipelineNotifications);
    }
    if (triggerNotifications != null) {
      notifications.addAll(triggerNotifications);
    }
    return notifications;
  }
}
