/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.echo.model.trigger.NexusEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link NexusEvent}, which occur when an
 * artifact is added/modified in anNexus repository.
 */
@Component
public class NexusEventHandler extends BaseTriggerEventHandler<NexusEvent> {
  private static final String NEXUS_TRIGGER_TYPE = "nexus";
  private static final List<String> supportedTriggerTypes =
      Collections.singletonList(NEXUS_TRIGGER_TYPE);

  public NexusEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(NexusEvent.TYPE);
  }

  @Override
  protected Class<NexusEvent> getEventType() {
    return NexusEvent.class;
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
        && NEXUS_TRIGGER_TYPE.equals(trigger.getType())
        && trigger.getNexusSearchName() != null;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(NexusEvent event) {
    return event != null;
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(NexusEvent nexusEvent) {
    String searchName = nexusEvent.getContent().getName();
    return trigger ->
        trigger.getType().equals(NEXUS_TRIGGER_TYPE)
            && trigger.getNexusSearchName().equals(searchName);
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(NexusEvent nexusEvent, Trigger trigger) {
    return nexusEvent.getContent() != null && nexusEvent.getContent().getArtifact() != null
        ? Collections.singletonList(nexusEvent.getContent().getArtifact())
        : new ArrayList<>();
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(NexusEvent event) {
    return trigger ->
        trigger.atNexusSearchName(event.getContent().getName()).atEventId(event.getEventId());
  }
}
