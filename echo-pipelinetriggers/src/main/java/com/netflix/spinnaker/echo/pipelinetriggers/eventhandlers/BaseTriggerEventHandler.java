/*
 * Copyright 2018 Google, Inc.
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
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Base implementation of {@link TriggerEventHandler} for events that require looking for matching
 * triggers on a pipeline. Most events fall into this category; at this point the only exception is
 * manual events.
 */
@Slf4j
public abstract class BaseTriggerEventHandler<T extends TriggerEvent> implements TriggerEventHandler<T> {
  private final Registry registry;
  protected final ObjectMapper objectMapper;

  BaseTriggerEventHandler(Registry registry, ObjectMapper objectMapper) {
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  public Optional<Pipeline> withMatchingTrigger(T event, Pipeline pipeline) {
    if (pipeline.getTriggers() == null || pipeline.isDisabled()) {
      return Optional.empty();
    } else {
      try {
        return pipeline.getTriggers()
          .stream()
          .filter(this::isValidTrigger)
          .filter(matchTriggerFor(event, pipeline))
          .findFirst()
          .map(buildTrigger(pipeline, event));
      } catch (Exception e) {
        onSubscriberError(e);
        return Optional.empty();
      }
    }
  }

  private void onSubscriberError(Throwable error) {
    log.error("Subscriber raised an error processing pipeline", error);
    registry.counter("trigger.errors", "exception", error.getClass().getName()).increment();
  }

  @Override
  public T convertEvent(Event event) {
    return  objectMapper.convertValue(event, getEventType());
  }

  protected abstract Predicate<Trigger> matchTriggerFor(T event, Pipeline pipeline);

  protected abstract Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, T event);

  protected abstract boolean isValidTrigger(Trigger trigger);

  protected abstract Class<T> getEventType();
}
