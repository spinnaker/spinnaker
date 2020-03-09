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
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.batching.BatchingSettings;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.config.GooglePubsubCredentialsProvider;
import com.netflix.spinnaker.echo.config.GooglePubsubProperties.Content;
import com.netflix.spinnaker.echo.config.GooglePubsubProperties.GooglePubsubPublisherConfig;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.model.PubsubPublisher;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.threeten.bp.Duration;

@Data
@Slf4j
public class GooglePubsubPublisher implements PubsubPublisher {

  private final PubsubSystem pubsubSystem = PubsubSystem.GOOGLE;

  private String name;

  private String topicName;

  private String fullTopicName;

  private Content content;

  private Publisher publisher;

  private ObjectMapper mapper;

  public static GooglePubsubPublisher buildPublisher(
      GooglePubsubPublisherConfig config, ObjectMapper mapper) {
    GooglePubsubPublisher publisher = new GooglePubsubPublisher();
    publisher.setName(config.getName());
    ProjectTopicName fullName = ProjectTopicName.of(config.getProject(), config.getTopicName());
    publisher.setTopicName(config.getTopicName());
    publisher.setFullTopicName(fullName.toString());
    publisher.setContent(config.getContent());
    publisher.setMapper(mapper);

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder()
            .setElementCountThreshold(config.getBatchCountThreshold())
            .setDelayThreshold(Duration.ofMillis(config.getDelayMillisecondsThreshold()))
            .build();

    try {
      Publisher p =
          Publisher.newBuilder(fullName)
              .setCredentialsProvider(new GooglePubsubCredentialsProvider(config.getJsonPath()))
              .setBatchingSettings(batchingSettings)
              .build();
      publisher.setPublisher(p);
    } catch (IOException ioe) {
      log.error("Could not create Google Pubsub Publishers", ioe);
    }

    return publisher;
  }

  public void publishEvent(Event event) {
    String jsonPayload;
    try {
      jsonPayload = mapper.writeValueAsString(event);
    } catch (JsonProcessingException jpe) {
      log.error("Could not serialize event message", jpe);
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

    publish(jsonPayload, attributes);
  }

  public void publish(Map payload, Map<String, String> attributes) {
    String jsonPayload;
    try {
      jsonPayload = mapper.writeValueAsString(payload);
    } catch (JsonProcessingException jpe) {
      log.error("Could not serialize event message", jpe);
      return;
    }
    publish(jsonPayload, attributes);
  }

  public void publish(String jsonPayload, Map<String, String> attributes) {
    PubsubMessage message =
        PubsubMessage.newBuilder()
            .setData(ByteString.copyFromUtf8(jsonPayload))
            .putAllAttributes(attributes)
            .build();

    log.debug("Publishing message on Google Pubsub topic {}", this.getFullTopicName());

    ApiFuture<String> future = publisher.publish(message);
    ApiFutures.addCallback(future, new PublishCallback(this.getFullTopicName()));
  }

  @RequiredArgsConstructor
  private class PublishCallback implements ApiFutureCallback<String> {

    private final String topic;

    @Override
    public void onFailure(Throwable t) {
      log.error("Could not publish message to Google Pubsub topic " + this.topic, t);
    }

    @Override
    public void onSuccess(String result) {
      log.debug("Successfully published message with ID {}", result);
    }
  }
}
