/*
 * Copyright 2025 Harness, Inc.
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Java implementation of the Groovy EventHandlerSupport class. Provides helper methods for creating
 * mocked PipelineCache instances for tests.
 */
public class TestEventHandlerSupport {

  /**
   * Creates a mocked PipelineCache for the given pipelines.
   *
   * @param pipelines Array of pipelines to include in the cache
   * @return A mocked PipelineCache instance
   */
  public PipelineCache pipelineCache(Pipeline... pipelines) {
    return pipelineCache(Arrays.asList(pipelines));
  }

  /**
   * Creates a mocked PipelineCache for the given pipelines.
   *
   * @param pipelines List of pipelines to include in the cache
   * @return A mocked PipelineCache instance
   */
  public PipelineCache pipelineCache(List<Pipeline> pipelines) {
    PipelineCache cache = mock(PipelineCache.class);
    List<Pipeline> decoratedPipelines = PipelineCache.decorateTriggers(pipelines);
    Map<String, List<Trigger>> enabledTriggers = extractEnabledTriggersFrom(decoratedPipelines);

    try {
      when(cache.getEnabledTriggersSync()).thenReturn(enabledTriggers);
    } catch (TimeoutException e) {
      throw new RuntimeException("Failed to mock PipelineCache", e);
    }

    return cache;
  }

  /**
   * Extract enabled triggers from a list of pipelines. This is a copy of the method from
   * PipelineCache
   *
   * @param pipelines List of pipelines to extract triggers from
   * @return Map of trigger type to list of enabled triggers
   */
  private static Map<String, List<Trigger>> extractEnabledTriggersFrom(List<Pipeline> pipelines) {
    return pipelines.stream()
        .filter(p -> !p.isDisabled())
        .flatMap(p -> Optional.ofNullable(p.getTriggers()).orElse(Collections.emptyList()).stream())
        .filter(Trigger::isEnabled)
        .filter(t -> t.getType() != null)
        .collect(Collectors.groupingBy(Trigger::getType));
  }
}
