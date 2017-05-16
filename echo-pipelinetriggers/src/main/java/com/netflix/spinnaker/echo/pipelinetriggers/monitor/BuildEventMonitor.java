/*
 * Copyright 2016 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
public class BuildEventMonitor extends TriggerMonitor {

  public static final String[] BUILD_TRIGGER_TYPES = {"jenkins", "travis"};

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;

  @Autowired
  public BuildEventMonitor(@NonNull PipelineCache pipelineCache,
                           @NonNull Action1<Pipeline> subscriber,
                           @NonNull Registry registry) {
    super(subscriber, registry);
    this.pipelineCache = pipelineCache;
  }

  @Override
  public void processEvent(Event event) {
    super.validateEvent(event);
    if (!event.getDetails().getType().equalsIgnoreCase(BuildEvent.TYPE)) {
      return;
    }

    BuildEvent buildEvent = objectMapper.convertValue(event, BuildEvent.class);
    Observable.just(buildEvent)
      .doOnNext(this::onEchoResponse)
      .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
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
    return trigger -> pipeline.withTrigger(trigger.atBuildNumber(buildEvent.getBuildNumber()));
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
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event) {
    BuildEvent buildEvent = (BuildEvent) event;
    String jobName = buildEvent.getContent().getProject().getName();
    String master = buildEvent.getContent().getMaster();
    return trigger -> isBuildTrigger(trigger) && trigger.getJob().equals(jobName) && trigger.getMaster().equals(master);
  }

  @Override
  protected void onMatchingPipeline(Pipeline pipeline) {
    super.onMatchingPipeline(pipeline);
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName());
    if (isBuildTrigger(pipeline.getTrigger())) {
      id.withTag("job", pipeline.getTrigger().getJob());
    }
    registry.counter(id).increment();
  }

  private boolean isBuildTrigger(Trigger trigger) {
    return Arrays.stream(BUILD_TRIGGER_TYPES).anyMatch(triggerType -> triggerType.equals(trigger.getType()));
  }
}
