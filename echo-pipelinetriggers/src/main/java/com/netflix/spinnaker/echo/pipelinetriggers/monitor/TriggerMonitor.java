/*
 * Copyright 2016 Google, Inc.
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

import com.google.common.base.Strings;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.TriggerEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator;
import com.netflix.spinnaker.echo.pipelinetriggers.postprocessors.PipelinePostProcessorHandler;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Triggers pipelines in orca when a TriggerEvent of type T is received by echo. */
@Slf4j
public class TriggerMonitor<T extends TriggerEvent> implements EventListener {
  private final PipelineInitiator pipelineInitiator;
  private final Registry registry;
  private final PipelineCache pipelineCache;
  private final PipelinePostProcessorHandler pipelinePostProcessorHandler;
  private final TriggerEventHandler<T> eventHandler;

  TriggerMonitor(
      @NonNull PipelineCache pipelineCache,
      @NonNull PipelineInitiator pipelineInitiator,
      @NonNull Registry registry,
      @NonNull PipelinePostProcessorHandler pipelinePostProcessorHandler,
      @NonNull TriggerEventHandler<T> eventHandler) {
    this.pipelineCache = pipelineCache;
    this.pipelineInitiator = pipelineInitiator;
    this.registry = registry;
    this.pipelinePostProcessorHandler = pipelinePostProcessorHandler;
    this.eventHandler = eventHandler;
  }

  public void processEvent(Event event) {
    validateEvent(event);
    if (eventHandler.handleEventType(event.getDetails().getType())) {
      recordMetrics();
      T triggerEvent = eventHandler.convertEvent(event);
      triggerMatchingPipelines(triggerEvent);
    }
  }

  private void validateEvent(Event event) {
    if (event.getDetails() == null) {
      throw new IllegalArgumentException("Event details required by the event monitor.");
    } else if (event.getDetails().getType() == null) {
      throw new IllegalArgumentException("Event details type required by the event monitor.");
    }
  }

  private void triggerMatchingPipelines(T event) {
    try {
      List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelineCache);
      matchingPipelines.stream()
          .filter(p -> Strings.isNullOrEmpty(p.getErrorMessage()))
          .map(pipelinePostProcessorHandler::process)
          .forEach(
              p -> {
                recordMatchingPipeline(p);
                pipelineInitiator.startPipeline(p, PipelineInitiator.TriggerSource.EVENT);
              });

      matchingPipelines.stream()
          .filter(p -> !Strings.isNullOrEmpty(p.getErrorMessage()))
          .forEach(pipelineInitiator::recordPipelineFailure);
    } catch (TimeoutException e) {
      log.error("Failed to get pipeline configs", e);
    }
  }

  private void recordMetrics() {
    registry.counter("echo.events.processed").increment();
  }

  private void recordMatchingPipeline(Pipeline pipeline) {
    log.info("Found matching pipeline {}:{}", pipeline.getApplication(), pipeline.getName());
    emitMetricsOnMatchingPipeline(pipeline);
  }

  private void emitMetricsOnMatchingPipeline(Pipeline pipeline) {
    Id id =
        registry
            .createId("pipelines.triggered")
            .withTag("monitor", eventHandler.getClass().getSimpleName())
            .withTag("application", pipeline.getApplication())
            .withTags(eventHandler.getAdditionalTags(pipeline));
    registry.counter(id).increment();
  }
}
