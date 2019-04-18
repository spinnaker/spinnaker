/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.echo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.interpret.FatalTemplateErrorsException;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Metadata;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.PubsubEventHandler;
import com.netflix.spinnaker.echo.pubsub.model.EventCreator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * An EventCreator that extracts artifacts from an incoming message and creates an event of type pubsub from the
 * message and artifacts.
 */
@Slf4j
public class PubsubEventCreator implements EventCreator {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Optional<MessageArtifactTranslator> messageArtifactTranslator;

  public PubsubEventCreator(Optional<MessageArtifactTranslator> messageArtifactTranslator) {
    this.messageArtifactTranslator = messageArtifactTranslator;
  }

  public Event createEvent(MessageDescription description) {
    log.trace("Processing pubsub event with payload {}", description.getMessagePayload());

    try {
      description.setArtifacts(parseArtifacts(description.getMessagePayload()));
    } catch (FatalTemplateErrorsException e) {
      log.error("Template failed to process artifacts for message {}", description, e);
    }

    Event event = new Event();
    Map<String, Object> content = new HashMap<>();
    Metadata details = new Metadata();

    try {
      event.setPayload(objectMapper.readValue(description.getMessagePayload(), Map.class));
    } catch (IOException e) {
      log.warn("Could not parse message payload as JSON", e);
    }

    content.put("messageDescription", description);
    details.setType(PubsubEventHandler.PUBSUB_TRIGGER_TYPE);

    if (description.getMessageAttributes() != null) {
      details.setAttributes(description.getMessageAttributes());
    }

    event.setContent(content);
    event.setDetails(details);
    return event;
  }

  private List<Artifact> parseArtifacts(String messagePayload) {
    if (!messageArtifactTranslator.isPresent()) {
      return Collections.emptyList();
    }
    List<Artifact> artifacts = messageArtifactTranslator.get().parseArtifacts(messagePayload);
    // Artifact must have at least a reference defined.
    if (CollectionUtils.isEmpty(artifacts) || StringUtils.isEmpty(artifacts.get(0).getReference())) {
      return Collections.emptyList();
    }
    return artifacts;
  }
}
