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

import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Interface for classes that match a TriggerEvent to pipelines that should be triggered in response
 * to the event.
 */
public interface TriggerEventHandler<T extends TriggerEvent> {
  /** @return the list of trigger types */
  List<String> supportedTriggerTypes();

  /**
   * @param eventType An event type
   * @return True if the implementing class handles this event type
   */
  boolean handleEventType(String eventType);

  /**
   * Converts an event to the type handled by the implementing class
   *
   * @param event The event to convert
   * @return The converted event
   */
  T convertEvent(Event event);

  /**
   * Given a list of pipelines and an event, returns the pipelines that should be triggered by the
   * event
   *
   * @param event The triggering event
   * @param pipelineCache a source for pipelines and triggers to consider
   * @return The pipelines that should be triggered
   */
  List<Pipeline> getMatchingPipelines(T event, PipelineCache pipelineCache) throws TimeoutException;

  /**
   * Given a pipeline, gets any additional tags that should be associated with metrics recorded
   * about that pipeline
   *
   * @param pipeline The pipeline
   * @return Tags to be included in metrics
   */
  default Map<String, String> getAdditionalTags(Pipeline pipeline) {
    return new HashMap<>();
  }

  default boolean isSuccessfulTriggerEvent(T event) {
    return true;
  }
}
