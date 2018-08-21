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

package com.netflix.spinnaker.echo.pubsub.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Metadata;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubPublishers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty("pubsub.google.enabled")
public class GooglePubsubEventListener implements EchoEventListener {

  @Autowired
  private PubsubPublishers publishers;

  @Autowired
  ObjectMapper mapper;

  @Override
  public void processEvent(Event event) {
    publishers.publishersMatchingType(PubsubSystem.GOOGLE)
      .stream()
      .map(p -> (GooglePubsubPublisher) p)
      .forEach(p -> publishEvent(p, event));
  }

  private void publishEvent(GooglePubsubPublisher p, Event event) {
    String jsonPayload;
    try {
      jsonPayload = mapper.writeValueAsString(event);
    } catch (JsonProcessingException jpe) {
      log.error("Could not serialize event message: {}", jpe);
      return;
    }

    Map<String, String> attributes = new HashMap<>();
    if (event.getDetails() != null) {
      Metadata m = event.getDetails();

      String rawType = m.getType();
      if (StringUtils.isNotEmpty(rawType)) {
        attributes.put("rawType", rawType);

        String[] eventDetails = rawType.split(":");
        if (eventDetails.length == 3) {
          attributes.put("source", eventDetails[0]);
          attributes.put("type", eventDetails[1]);
          attributes.put("status", eventDetails[2]);
        }
      }

      if (StringUtils.isNotEmpty(m.getApplication())) {
        attributes.put("application", m.getApplication());
      }

      if (m.getAttributes() != null && !m.getAttributes().isEmpty()) {
        attributes.putAll(m.getAttributes());
      }
    }

    if (event.getContent() != null && !event.getContent().isEmpty()) {
      Map content = event.getContent();

      String name = content.getOrDefault("name", "").toString();
      if (StringUtils.isNotEmpty(name)) {
        attributes.put("name", name);
      }

      String taskName = content.getOrDefault("taskName", "").toString();
      if (StringUtils.isNotEmpty(taskName)) {
        attributes.put("taskName", taskName);
      }
    }

    p.publish(jsonPayload, attributes);
  }
}
