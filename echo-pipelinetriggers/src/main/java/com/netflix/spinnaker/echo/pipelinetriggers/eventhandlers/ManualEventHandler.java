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
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.artifacts.ArtifactInfoService;
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent.Content;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Implementation of TriggerEventHandler for events of type {@link ManualEvent}, which occur when a
 * user manually requests a particular pipeline to execute (possibly supplying parameters to include
 * in the trigger). This event handler is unique in that the trigger uniquely specifies which
 * pipeline to execute; rather than looking at pipeline triggers for one that matches the event, it
 * simply looks for the pipeline whose application and id/name match the manual execution request.
 */
@Component
public class ManualEventHandler implements TriggerEventHandler<ManualEvent> {
  private static final String MANUAL_TRIGGER_TYPE = "manual";
  private static final Logger log = LoggerFactory.getLogger(ManualEventHandler.class);
  private static final List<String> supportedTriggerTypes =
      Collections.singletonList(MANUAL_TRIGGER_TYPE);

  private final ObjectMapper objectMapper;
  private final Optional<BuildInfoService> buildInfoService;
  private final Optional<ArtifactInfoService> artifactInfoService;
  private final PipelineCache pipelineCache;

  @Autowired
  public ManualEventHandler(
      ObjectMapper objectMapper,
      Optional<BuildInfoService> buildInfoService,
      Optional<ArtifactInfoService> artifactInfoService,
      PipelineCache pipelineCache) {
    this.objectMapper = objectMapper;
    this.buildInfoService = buildInfoService;
    this.artifactInfoService = artifactInfoService;
    this.pipelineCache = pipelineCache;
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(ManualEvent.TYPE);
  }

  @Override
  public ManualEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, ManualEvent.class);
  }

  private Optional<Pipeline> withMatchingTrigger(ManualEvent manualEvent, Pipeline pipeline) {
    Content content = manualEvent.getContent();
    String application = content.getApplication();
    String pipelineNameOrId = content.getPipelineNameOrId();
    if (pipelineMatches(application, pipelineNameOrId, pipeline)) {
      try {
        return Optional.of(buildTrigger(pipelineCache.refresh(pipeline), content.getTrigger()));
      } catch (Exception e) {
        return Optional.of(pipeline.withErrorMessage(e.toString()));
      }
    } else {
      return Optional.empty();
    }
  }

  @Override
  public List<Pipeline> getMatchingPipelines(ManualEvent event, PipelineCache pipelineCache)
      throws TimeoutException {
    if (!isSuccessfulTriggerEvent(event)) {
      return Collections.emptyList();
    }

    List<Pipeline> retval =
        pipelineCache.getPipelinesSync().stream()
            .map(p -> withMatchingTrigger(event, p))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

    // If there's no pipeline in the cache with a matching trigger, query for
    // it.  This is expected if PipelineCache is only caching (enabled)
    // pipelines with (enabled) triggers of specific types.
    if (retval.isEmpty() && pipelineCache.isFilterFront50Pipelines()) {
      Optional<Pipeline> pipeline = getPipelineForEvent(event, pipelineCache);
      return pipeline.map(p -> List.of(p)).orElse(List.of());
    }

    return retval;
  }

  /**
   * Query front50 (via the pipeline cache) for a pipeline by id, and if necessary name,
   * corresponding to a manual trigger event.
   */
  private Optional<Pipeline> getPipelineForEvent(ManualEvent event, PipelineCache pipelineCache) {
    Content content = event.getContent();
    String application = content.getApplication();
    String pipelineNameOrId = content.getPipelineNameOrId();
    try {
      // Manual execution in deck specifies the pipeline name, so try that first.  If that doesn't
      // work, query by id.
      Optional<Pipeline> pipeline =
          pipelineCache
              .getPipelineByName(application, pipelineNameOrId)
              .or(() -> pipelineCache.getPipelineById(pipelineNameOrId));

      if (pipeline.isEmpty()) {
        // no luck, we queried, but still can't find the pipeline
        return Optional.empty();
      }

      Pipeline actualPipeline = pipeline.get();

      // It's a bit of belt-and-suspenders, but since we have logic that
      // verifies that a pipeline from the cache matches a trigger (see
      // withMatchingTrigger call pipelineMatches), let's verify that this
      // pipeline matches the trigger as well.  This also handles the disabled
      // check.
      if (!pipelineMatches(application, pipelineNameOrId, actualPipeline)) {
        log.debug(
            "pipeline from front50 doesn't match trigger.  trigger: {}, pipeline id {}, pipeline application, pipeline name",
            event,
            actualPipeline.getId(),
            actualPipeline.getApplication(),
            actualPipeline.getName());
        return Optional.empty();
      }

      return Optional.of(buildTrigger(actualPipeline, content.getTrigger()));
    } catch (Exception e) {
      // This isn't my favorite way of doing error handling, but it's what
      // buildTrigger does, and what TriggerMonitor.triggerMatchingPipelines
      // expects.
      log.error(
          String.format(
              "exception querying for pipeline in application %s with nameOrId %s",
              application, pipelineNameOrId),
          e);
      // We don't know whether we have a pipeline name or id...but name is a
      // required field in a pipeline, so use it there.
      return Optional.of(
          Pipeline.builder()
              .application(application)
              .name(pipelineNameOrId)
              .errorMessage(e.toString())
              .build());
    }
  }

  private boolean pipelineMatches(String application, String nameOrId, Pipeline pipeline) {
    return !pipeline.isDisabled()
        && pipeline.getApplication().equals(application)
        && (pipeline.getName().equals(nameOrId) || pipeline.getId().equals(nameOrId));
  }

  protected Pipeline buildTrigger(Pipeline pipeline, Trigger manualTrigger) {
    List<Map<String, Object>> notifications =
        buildNotifications(pipeline.getNotifications(), manualTrigger.getNotifications());
    Trigger trigger = manualTrigger.atPropagateAuth(true);
    List<Artifact> artifacts = new ArrayList<>();
    String master = manualTrigger.getMaster();
    String job = manualTrigger.getJob();
    Integer buildNumber = manualTrigger.getBuildNumber();

    ArrayList<String> pipelineErrors = new ArrayList<>();

    if (buildInfoService.isPresent()
        && StringUtils.isNoneEmpty(master, job)
        && buildNumber != null) {
      BuildEvent buildEvent = buildInfoService.get().getBuildEvent(master, job, buildNumber);
      try {
        trigger = trigger.withBuildInfo(buildInfoService.get().getBuildInfo(buildEvent));
      } catch (Exception e) {
        pipelineErrors.add("Could not get build from build server: " + e.toString());
      }

      try {
        trigger =
            trigger.withProperties(
                buildInfoService.get().getProperties(buildEvent, manualTrigger.getPropertyFile()));
      } catch (Exception e) {
        pipelineErrors.add("Could not get property file from build server: " + e.toString());
      }

      try {
        artifacts.addAll(
            buildInfoService.get().getArtifactsFromBuildEvent(buildEvent, manualTrigger));
      } catch (Exception e) {
        pipelineErrors.add("Could not get all artifacts from build server: " + e.toString());
      }
    }

    try {
      if (artifactInfoService.isPresent()
          && !CollectionUtils.isEmpty(manualTrigger.getArtifacts())) {
        List<Artifact> resolvedArtifacts = resolveArtifacts(manualTrigger.getArtifacts());
        artifacts.addAll(resolvedArtifacts);
        // update the artifacts on the manual trigger with the resolved artifacts
        trigger = trigger.withArtifacts(convertToListOfMaps(resolvedArtifacts));
      }
    } catch (Exception e) {
      pipelineErrors.add("Could not resolve artifacts: " + e.toString());
    }

    if (!pipelineErrors.isEmpty()) {
      pipeline = pipeline.withErrorMessage(String.join("\n", pipelineErrors));
    }

    return pipeline
        .withTrigger(trigger)
        .withNotifications(notifications)
        .withReceivedArtifacts(artifacts);
  }

  private List<Map<String, Object>> convertToListOfMaps(List<Artifact> artifacts) {
    return objectMapper.convertValue(artifacts, new TypeReference<List<Map<String, Object>>>() {});
  }

  /**
   * If possible, replace trigger artifact with full artifact from the artifactInfoService. If there
   * are no artifactInfo providers, or if the artifact is not found, the artifact is returned as is.
   */
  protected List<Artifact> resolveArtifacts(List<Map<String, Object>> manualTriggerArtifacts) {
    List<Artifact> resolvedArtifacts = new ArrayList<>();
    for (Map a : manualTriggerArtifacts) {
      Artifact artifact = objectMapper.convertValue(a, Artifact.class);

      if (Strings.isNullOrEmpty(artifact.getName())
          || Strings.isNullOrEmpty(artifact.getVersion())
          || Strings.isNullOrEmpty(artifact.getLocation())) {
        resolvedArtifacts.add(artifact);
      } else {
        try {
          Artifact resolvedArtifact =
              artifactInfoService
                  .get()
                  .getArtifactByVersion(
                      artifact.getLocation(), artifact.getName(), artifact.getVersion());
          resolvedArtifacts.add(resolvedArtifact);
        } catch (SpinnakerHttpException e) {
          if (e.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
            log.error(
                "Artifact "
                    + artifact.getName()
                    + " "
                    + artifact.getVersion()
                    + " not found in image provider "
                    + artifact.getLocation());
            resolvedArtifacts.add(artifact);
          } else throw e;
        }
      }
    }
    return resolvedArtifacts;
  }

  private List<Map<String, Object>> buildNotifications(
      List<Map<String, Object>> pipelineNotifications,
      List<Map<String, Object>> triggerNotifications) {
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
