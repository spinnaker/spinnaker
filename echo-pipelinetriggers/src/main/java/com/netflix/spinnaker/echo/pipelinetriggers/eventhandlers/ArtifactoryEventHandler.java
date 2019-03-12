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
import com.netflix.spinnaker.echo.model.trigger.ArtifactoryEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.JinjaArtifactExtractor;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implementation of TriggerEventHandler for events of type {@link ArtifactoryEvent}, which occur when
 * an artifact is added/modified in an Artifactory repository.
 */
@Component
public class ArtifactoryEventHandler extends BaseTriggerEventHandler<ArtifactoryEvent> {
  private static final String ARTIFACTORY_TRIGGER_TYPE = "artifactory";

  public ArtifactoryEventHandler(Registry registry, ObjectMapper objectMapper, JinjaArtifactExtractor jinjaArtifactExtractor) {
    super(registry, objectMapper, jinjaArtifactExtractor);
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(ArtifactoryEvent.TYPE);
  }

  @Override
  protected Class<ArtifactoryEvent> getEventType() {
    return ArtifactoryEvent.class;
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
      && ARTIFACTORY_TRIGGER_TYPE.equals(trigger.getType())
      && trigger.getArtifactorySearchName() != null;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(ArtifactoryEvent event) {
    return event != null;
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(ArtifactoryEvent artifactoryEvent) {
    String artifactorySearchName = artifactoryEvent.getContent().getName();
    return trigger -> trigger.getType().equals(ARTIFACTORY_TRIGGER_TYPE)
      && trigger.getArtifactorySearchName().equals(artifactorySearchName);
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(ArtifactoryEvent artifactoryEvent) {
    return artifactoryEvent.getContent() != null && artifactoryEvent.getContent().getArtifact() != null ?
      Collections.singletonList(artifactoryEvent.getContent().getArtifact())
      : new ArrayList<>();
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(ArtifactoryEvent event) {
    return trigger -> trigger
      .atArtifactorySearchName(event.getContent().getName())
      .atEventId(event.getEventId());
  }
}
