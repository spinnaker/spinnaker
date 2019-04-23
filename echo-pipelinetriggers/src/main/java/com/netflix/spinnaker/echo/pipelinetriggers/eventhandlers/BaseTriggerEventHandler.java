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
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  @Override
  public List<Pipeline> getMatchingPipelines(T event, PipelineCache pipelineCache) throws TimeoutException {
    if (!isSuccessfulTriggerEvent(event)) {
      return Collections.emptyList();
    }

    Map<String, List<Trigger>> triggers = pipelineCache.getEnabledTriggersSync();
    return supportedTriggerTypes().stream()
      .flatMap(triggerType -> Optional.ofNullable(triggers.get(triggerType)).orElse(Collections.emptyList()).stream())
      .filter(this::isValidTrigger)
      .filter(matchTriggerFor(event))
      .map(trigger -> withMatchingTrigger(event, trigger))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .distinct()
      .collect(Collectors.toList());
  }

  private Optional<Pipeline> withMatchingTrigger(T event, Trigger trigger) {
    try {
      return Stream.of(trigger)
        .map(buildTrigger(event))
        .map(t -> new TriggerWithArtifacts(t, getArtifacts(event, t)))
        .filter(ta -> ArtifactMatcher.anyArtifactsMatchExpected(ta.artifacts, ta.trigger, ta.trigger.getParent().getExpectedArtifacts()))
        .findFirst()
        .map(ta -> ta.trigger.getParent().withTrigger(ta.trigger).withReceivedArtifacts(ta.artifacts));
    } catch (Exception e) {
      onSubscriberError(e);
      return Optional.empty();
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

  private List<Artifact> getArtifacts(T event, Trigger trigger) {
    List<Artifact> results = new ArrayList<>();
    Optional.ofNullable(getArtifactsFromEvent(event, trigger)).ifPresent(results::addAll);
    return results;
  }

  protected abstract Predicate<Trigger> matchTriggerFor(T event);

  protected abstract Function<Trigger, Trigger> buildTrigger(T event);

  protected abstract boolean isValidTrigger(Trigger trigger);

  protected abstract Class<T> getEventType();

  protected abstract List<Artifact> getArtifactsFromEvent(T event, Trigger trigger);

  @Value
  private class TriggerWithArtifacts {
    Trigger trigger;
    List<Artifact> artifacts;
  }
}
