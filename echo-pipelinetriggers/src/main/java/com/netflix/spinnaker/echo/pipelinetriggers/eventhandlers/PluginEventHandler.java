/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.PluginEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class PluginEventHandler extends BaseTriggerEventHandler<PluginEvent> {

  private static final String PLUGIN_TRIGGER_TYPE = "plugin";
  private static final List<String> SUPPORTED_TYPES =
      Collections.singletonList(PLUGIN_TRIGGER_TYPE);

  public PluginEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(PluginEvent event) {
    return trigger -> trigger.getType().equals(PLUGIN_TRIGGER_TYPE);
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(PluginEvent event) {
    return trigger ->
        trigger
            .toBuilder()
            .pluginId(event.getContent().getPluginId())
            .description(event.getContent().getDescription())
            .provider(event.getContent().getProvider())
            .version(event.getContent().getVersion())
            .releaseDate(event.getContent().getReleaseDate())
            .requires(event.getContent().getRequires())
            .parsedRequires(event.getContent().getParsedRequires())
            .binaryUrl(event.getContent().getBinaryUrl())
            .sha512sum(event.getContent().getSha512sum())
            .build();
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled() && trigger.getType().equals(PLUGIN_TRIGGER_TYPE);
  }

  @Override
  protected Class<PluginEvent> getEventType() {
    return PluginEvent.class;
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(PluginEvent event, Trigger trigger) {
    // TODO(rz): Add artifact support someday
    return new ArrayList<>();
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return SUPPORTED_TYPES;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(PluginEvent.TYPE);
  }
}
