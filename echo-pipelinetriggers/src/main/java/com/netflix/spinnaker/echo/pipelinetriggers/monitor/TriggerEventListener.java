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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.TriggerEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator;
import com.netflix.spinnaker.echo.pipelinetriggers.postprocessors.PipelinePostProcessorHandler;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Listens for TriggerEvents and sends them out to any registered TriggerMonitors, which in turn
 * handle finding an triggering pipelines orca.
 */
@Slf4j
@Component
public class TriggerEventListener implements EchoEventListener {
  private final List<TriggerMonitor> triggerMonitors;

  public TriggerEventListener(
      @NonNull PipelineCache pipelineCache,
      @NonNull PipelineInitiator pipelineInitiator,
      @NonNull Registry registry,
      @NonNull PipelinePostProcessorHandler pipelinePostProcessorHandler,
      @NonNull List<TriggerEventHandler<?>> eventHandlers) {
    this.triggerMonitors =
        eventHandlers.stream()
            .map(
                e ->
                    new TriggerMonitor<>(
                        pipelineCache,
                        pipelineInitiator,
                        registry,
                        pipelinePostProcessorHandler,
                        e))
            .collect(Collectors.toList());
  }

  public void processEvent(Event event) {
    for (TriggerMonitor triggerMonitor : triggerMonitors) {
      triggerMonitor.processEvent(event);
    }
  }
}
