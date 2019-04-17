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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.echo.artifacts.ArtifactInfoService;
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent.Content;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import retrofit.RetrofitError;

import java.util.*;

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
  private static final Logger log = LoggerFactory.getLogger(ManualEventHandler.class);

  private final ObjectMapper objectMapper;
  private final Optional<BuildInfoService> buildInfoService;
  private final Optional<ArtifactInfoService> artifactInfoService;
  private final PipelineCache pipelineCache;

  @Autowired
  public ManualEventHandler(
    ObjectMapper objectMapper,
    Optional<BuildInfoService> buildInfoService,
    Optional<ArtifactInfoService> artifactInfoService,
    PipelineCache pipelineCache
  ) {
    this.objectMapper = objectMapper;
    this.buildInfoService = buildInfoService;
    this.artifactInfoService = artifactInfoService;
    this.pipelineCache = pipelineCache;
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
      return Optional.of(buildTrigger(pipelineCache.refresh(pipeline), content.getTrigger()));
    } else {
      return Optional.empty();
    }
  }

  private boolean pipelineMatches(String application, String nameOrId, Pipeline pipeline) {
    return !pipeline.isDisabled()
      && pipeline.getApplication().equals(application)
      && (pipeline.getName().equals(nameOrId) || pipeline.getId().equals(nameOrId));
  }

  protected Pipeline buildTrigger(Pipeline pipeline, Trigger manualTrigger) {
    List<Map<String, Object>> notifications = buildNotifications(pipeline.getNotifications(), manualTrigger.getNotifications());
    Trigger trigger = manualTrigger.atPropagateAuth(true);
    List<Artifact> artifacts = new ArrayList<>();
    String master = manualTrigger.getMaster();
    String job = manualTrigger.getJob();
    Integer buildNumber = manualTrigger.getBuildNumber();
    if (buildInfoService.isPresent() && StringUtils.isNoneEmpty(master, job) && buildNumber != null) {
      BuildEvent buildEvent = buildInfoService.get().getBuildEvent(master, job, buildNumber);
      trigger = trigger
        .withBuildInfo(buildInfoService.get().getBuildInfo(buildEvent))
        .withProperties(buildInfoService.get().getProperties(buildEvent, manualTrigger.getPropertyFile()));
      artifacts.addAll(buildInfoService.get().getArtifactsFromBuildEvent(buildEvent, manualTrigger));
    }

    if (artifactInfoService.isPresent() && !CollectionUtils.isEmpty(manualTrigger.getArtifacts())) {
      List<Artifact> resolvedArtifacts = resolveArtifacts(manualTrigger.getArtifacts());
      artifacts.addAll(resolvedArtifacts);
      // update the artifacts on the manual trigger with the resolved artifacts
      trigger = trigger.withArtifacts(convertToListOfMaps(resolvedArtifacts));
    }

    return pipeline
      .withTrigger(trigger)
      .withNotifications(notifications)
      .withReceivedArtifacts(artifacts);
  }

  private List<Map<String, Object>> convertToListOfMaps(List<Artifact> artifacts) {
    return objectMapper.convertValue(artifacts, new TypeReference<List<Map<String,Object>>>() {});
  }

  /**
   * If possible, replace trigger artifact with full artifact from the artifactInfoService.
   * If there are no artifactInfo providers, or if the artifact is not found,
   *   the artifact is returned as is.
   */
  protected List<Artifact> resolveArtifacts(List<Map<String, Object>> manualTriggerArtifacts) {
    List<Artifact> resolvedArtifacts = new ArrayList<>();
    for (Map a : manualTriggerArtifacts) {
      Artifact artifact =  objectMapper.convertValue(a, Artifact.class);

      if (Strings.isNullOrEmpty(artifact.getName()) ||
        Strings.isNullOrEmpty(artifact.getVersion()) ||
        Strings.isNullOrEmpty(artifact.getLocation())) {
        log.error("Artifact does not have enough information to fetch. " +
          "Artifact must contain name, version, and location.");
        resolvedArtifacts.add(artifact);
      } else {
        try {
          Artifact resolvedArtifact = artifactInfoService.get()
            .getArtifactByVersion(artifact.getLocation(), artifact.getName(), artifact.getVersion());
          resolvedArtifacts.add(resolvedArtifact);
        } catch (RetrofitError e) {
          if (e.getResponse() != null && e.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
            log.error("Artifact " + artifact.getName() + " " + artifact.getVersion() +
              " not found in image provider " + artifact.getLocation());
            resolvedArtifacts.add(artifact);
          }
          else throw e;
        }
      }
    }
    return resolvedArtifacts;
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
