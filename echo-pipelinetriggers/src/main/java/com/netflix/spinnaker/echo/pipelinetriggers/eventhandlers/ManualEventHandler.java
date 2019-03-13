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
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent.Content;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Implementation of TriggerEventHandler for events of type {@link ManualEvent}, which occur when a
 * user manually requests a particular pipeline to execute (possibly supplying parameters to include
 * in the trigger). This event handler is unique in that the trigger uniquely specifies which pipeline
 * to execute; rather than looking at pipeline triggers for one that matches the event, it simply
 * looks for the pipeline whose application and id/name match the manual execution request.
 */
@Component
@RequiredArgsConstructor
public class ManualEventHandler implements TriggerEventHandler<ManualEvent> {
  private static final String MANUAL_TRIGGER_TYPE = "manual";

  private final ObjectMapper objectMapper;
  private final Optional<BuildInfoService> buildInfoService;

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
    Trigger trigger = manualTrigger.atPropagateAuth(true);
    List<Artifact> artifacts = Collections.emptyList();
    String master = manualTrigger.getMaster();
    String job = manualTrigger.getJob();
    if (buildInfoService.isPresent() && StringUtils.isNoneEmpty(master, job)) {
      BuildEvent buildEvent = buildInfoService.get().getBuildEvent(master, job, manualTrigger.getBuildNumber());
      trigger = trigger
        .withBuildInfo(buildInfoService.get().getBuildInfo(buildEvent))
        .withProperties(buildInfoService.get().getProperties(buildEvent, manualTrigger.getPropertyFile()));
      artifacts = buildInfoService.get().getArtifactsFromBuildEvent(buildEvent, manualTrigger);
    }
    return pipeline
      .withTrigger(trigger)
      .withNotifications(notifications)
      .withReceivedArtifacts(artifacts);
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
