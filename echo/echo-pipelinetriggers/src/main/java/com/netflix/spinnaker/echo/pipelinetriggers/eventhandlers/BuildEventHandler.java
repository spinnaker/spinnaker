/*
 * Copyright 2016 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isConstraintInPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link BuildEvent}, which occur when a
 * CI build completes.
 */
@Component
@Slf4j
public class BuildEventHandler extends BaseTriggerEventHandler<BuildEvent> {
  private static final String[] BUILD_TRIGGER_TYPES = {
    "jenkins", "travis", "concourse", "gitlab-ci"
  };
  private static final List<String> supportedTriggerTypes =
      Collections.unmodifiableList(Arrays.asList(BUILD_TRIGGER_TYPES));
  private final Optional<BuildInfoService> buildInfoService;

  @Autowired
  public BuildEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      Optional<BuildInfoService> buildInfoService,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
    this.buildInfoService = buildInfoService;
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(BuildEvent.TYPE);
  }

  @Override
  public Class<BuildEvent> getEventType() {
    return BuildEvent.class;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(BuildEvent buildEvent) {
    BuildEvent.Build lastBuild = buildEvent.getContent().getProject().getLastBuild();
    return lastBuild != null
        && !lastBuild.isBuilding()
        && lastBuild.getResult() == BuildEvent.Result.SUCCESS;
  }

  @Override
  public Function<Trigger, Trigger> buildTrigger(BuildEvent buildEvent) {
    return inputTrigger -> {
      Trigger trigger =
          inputTrigger
              .atBuildNumber(buildEvent.getBuildNumber())
              .withEventId(buildEvent.getEventId())
              .withLink(buildEvent.getContent().getProject().getLastBuild().getUrl());
      if (buildInfoService.isPresent()) {
        try {
          return AuthenticatedRequest.runAs(
                  getRunAsUser(trigger),
                  () ->
                      trigger
                          .withBuildInfo(buildInfoService.get().getBuildInfo(buildEvent))
                          .withProperties(
                              buildInfoService
                                  .get()
                                  .getProperties(buildEvent, inputTrigger.getPropertyFile())))
              .call();
        } catch (Exception e) {
          log.warn("Unable to add buildInfo and properties to trigger {}", trigger, e);
        }
      }
      return trigger;
    };
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
        && ((isBuildTrigger(trigger) && trigger.getJob() != null && trigger.getMaster() != null));
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(BuildEvent buildEvent) {
    String jobName = buildEvent.getContent().getProject().getName();
    String master = buildEvent.getContent().getMaster();
    return trigger ->
        isBuildTrigger(trigger)
            && trigger.getJob().equals(jobName)
            && trigger.getMaster().equals(master)
            && checkPayloadConstraintsMet(buildEvent, trigger);
  }

  private boolean isBuildTrigger(Trigger trigger) {
    return Arrays.stream(BUILD_TRIGGER_TYPES)
        .anyMatch(triggerType -> triggerType.equals(trigger.getType()));
  }

  protected List<Artifact> getArtifactsFromEvent(BuildEvent event, Trigger trigger) {
    if (buildInfoService.isPresent()) {
      try {
        return AuthenticatedRequest.runAs(
                getRunAsUser(trigger),
                () -> buildInfoService.get().getArtifactsFromBuildEvent(event, trigger))
            .call();
      } catch (Exception e) {
        log.warn("Unable to get artifacts from event {}, trigger {}", event, trigger, e);
      }
    }
    return Collections.emptyList();
  }

  /**
   * @param trigger the trigger from which to get the runAsUser
   * @return the runAsUser from the trigger if specified, otherwise 'anonymous'
   */
  @Nonnull
  private String getRunAsUser(Trigger trigger) {
    if (StringUtils.isNotBlank(trigger.getRunAsUser())) {
      return trigger.getRunAsUser().trim();
    }

    return "anonymous";
  }

  private boolean checkPayloadConstraintsMet(BuildEvent event, Trigger trigger) {
    if (trigger.getPayloadConstraints() == null) {
      return true; // No constraints, can trigger build
    }

    // Constraints are present, check they are all met
    Map buildProperties = getPropertiesFromEvent(event, trigger);
    boolean constraintsMet =
        buildProperties != null
            && isConstraintInPayload(trigger.getPayloadConstraints(), buildProperties);
    if (!constraintsMet) {
      log.info(
          "Constraints {} not met by build properties {}",
          trigger.getPayloadConstraints(),
          buildProperties);
    }
    return constraintsMet;
  }

  private Map getPropertiesFromEvent(BuildEvent event, Trigger trigger) {
    if (buildInfoService.isPresent()) {
      try {
        return AuthenticatedRequest.runAs(
                getRunAsUser(trigger),
                () -> buildInfoService.get().getProperties(event, trigger.getPropertyFile()))
            .call();
      } catch (Exception e) {
        log.warn("Unable to get artifacts from event {}, trigger {}", event, trigger, e);
      }
    }
    return Collections.emptyMap();
  }
}
