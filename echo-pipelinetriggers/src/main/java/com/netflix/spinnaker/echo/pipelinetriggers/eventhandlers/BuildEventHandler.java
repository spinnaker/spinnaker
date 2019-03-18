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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implementation of TriggerEventHandler for events of type {@link BuildEvent}, which occur when
 * a CI build completes.
 */
@Component
public class BuildEventHandler extends BaseTriggerEventHandler<BuildEvent> {
  private static final String[] BUILD_TRIGGER_TYPES = {"jenkins", "travis", "wercker", "concourse"};
  private final Optional<BuildInfoService> buildInfoService;

  @Autowired
  public BuildEventHandler(Registry registry, ObjectMapper objectMapper, Optional<BuildInfoService> buildInfoService) {
    super(registry, objectMapper);
    this.buildInfoService = buildInfoService;
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
    return lastBuild != null && !lastBuild.isBuilding() && lastBuild.getResult() == BuildEvent.Result.SUCCESS;
  }

  @Override
  public Function<Trigger, Trigger> buildTrigger(BuildEvent buildEvent) {
    return inputTrigger -> {
      Trigger trigger = inputTrigger.atBuildNumber(buildEvent.getBuildNumber())
        .withEventId(buildEvent.getEventId())
        .withLink(buildEvent.getContent().getProject().getLastBuild().getUrl());
      if (buildInfoService.isPresent()) {
        trigger = trigger.withBuildInfo(buildInfoService.get().getBuildInfo(buildEvent))
          .withProperties(buildInfoService.get().getProperties(buildEvent, inputTrigger.getPropertyFile()));
      }
      return trigger;
    };
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled() &&
      (
        (isBuildTrigger(trigger) &&
          trigger.getJob() != null &&
          trigger.getMaster() != null)
      );
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(BuildEvent buildEvent) {
    String jobName = buildEvent.getContent().getProject().getName();
    String master = buildEvent.getContent().getMaster();
    return trigger -> isBuildTrigger(trigger)
      && trigger.getJob().equals(jobName)
      && trigger.getMaster().equals(master);
  }

  private boolean isBuildTrigger(Trigger trigger) {
    return Arrays.stream(BUILD_TRIGGER_TYPES).anyMatch(triggerType -> triggerType.equals(trigger.getType()));
  }

  protected List<Artifact> getArtifactsFromEvent(BuildEvent event, Trigger trigger) {
    if (buildInfoService.isPresent()) {
      return buildInfoService.get().getArtifactsFromBuildEvent(event, trigger);
    }
    return Collections.emptyList();
  }
}
