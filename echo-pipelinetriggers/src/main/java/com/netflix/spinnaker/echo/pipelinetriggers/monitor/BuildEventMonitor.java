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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.anyArtifactsMatchExpected;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Action1;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
public class BuildEventMonitor extends TriggerMonitor {

  public static final String[] BUILD_TRIGGER_TYPES = {"jenkins", "travis", "wercker"};

  @Autowired
  public BuildEventMonitor(@NonNull PipelineCache pipelineCache,
                           @NonNull Action1<Pipeline> subscriber,
                           @NonNull Registry registry) {
    super(pipelineCache, subscriber, registry);
  }

  @Override
  protected boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(BuildEvent.TYPE);
  }

  @Override
  protected BuildEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, BuildEvent.class);
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    BuildEvent buildEvent = (BuildEvent) event;
    BuildEvent.Build lastBuild = buildEvent.getContent().getProject().getLastBuild();
    return lastBuild != null && !lastBuild.isBuilding() && lastBuild.getResult() == BuildEvent.Result.SUCCESS;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    BuildEvent buildEvent = (BuildEvent) event;
    return trigger -> pipeline.withTrigger(trigger.atBuildNumber(buildEvent.getBuildNumber())
                                                  .withEventId(event.getEventId())
                                                  .withLink(buildEvent.getContent().getProject().getLastBuild().getUrl()))
                              .withReceivedArtifacts(getArtifacts(buildEvent));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled() &&
      (
        (isBuildTrigger(trigger) &&
          trigger.getJob() != null &&
          trigger.getMaster() != null)
      );
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline) {
    BuildEvent buildEvent = (BuildEvent) event;
    String jobName = buildEvent.getContent().getProject().getName();
    String master = buildEvent.getContent().getMaster();
    return trigger -> isBuildTrigger(trigger)
      && trigger.getJob().equals(jobName)
      && trigger.getMaster().equals(master)
      && anyArtifactsMatchExpected(getArtifacts(buildEvent), trigger, pipeline);
  }

  @Override
  protected void emitMetricsOnMatchingPipeline(Pipeline pipeline) {
    val id = registry.createId("pipelines.triggered")
      .withTag("monitor", getClass().getSimpleName())
      .withTag("application", pipeline.getApplication());
    registry.counter(id).increment();
  }

  private boolean isBuildTrigger(Trigger trigger) {
    return Arrays.stream(BUILD_TRIGGER_TYPES).anyMatch(triggerType -> triggerType.equals(trigger.getType()));
  }

  private List<Artifact> getArtifacts(BuildEvent event) {
    return Optional.ofNullable(event.getContent())
      .map(BuildEvent.Content::getProject)
      .map(BuildEvent.Project::getLastBuild)
      .map(BuildEvent.Build::getArtifacts)
      .orElse(Collections.emptyList());
  }
}
