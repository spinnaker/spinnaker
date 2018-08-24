/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Metadata;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.pipelinetriggers.monitor.PubsubEventMonitor;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Shared cache of received and handled pubsub messages to synchronize clients.
 */
@Data
@Service
@Slf4j
public class PubsubMessageHandler {

  private final PubsubEventMonitor pubsubEventMonitor;
  private final ObjectMapper objectMapper;
  private RedisClientDelegate redisClientDelegate;
  private final Registry registry;

  private static final String SET_IF_NOT_EXIST = "NX";
  private static final String SET_EXPIRE_TIME_MILLIS = "PX";
  private static final String SUCCESS = "OK";
  @Autowired
  public PubsubMessageHandler(PubsubEventMonitor pubsubEventMonitor,
                              ObjectMapper objectMapper,
                              Optional<RedisClientSelector> redisClientSelector,
                              Registry registry) {
    this.pubsubEventMonitor = pubsubEventMonitor;
    this.objectMapper = objectMapper;
    redisClientSelector.ifPresent(selector -> this.redisClientDelegate = selector.primary("default"));
    this.registry = registry;
  }

  public void handleFailedMessage(MessageDescription description,
    MessageAcknowledger acknowledger,
    String identifier,
    String messageId) {
    String messageKey = makeProcessingKey(description, messageId);
    if (tryAck(messageKey, description.getAckDeadlineMillis(), acknowledger, identifier)) {
      setMessageHandled(messageKey, identifier, description.getRetentionDeadlineMillis());
    }
  }

  public void handleMessage(MessageDescription description,
                            MessageAcknowledger acknowledger,
                            String identifier,
                            String messageId) {
    if (redisClientDelegate == null) {
      throw new IllegalStateException("Redis not enabled, pubsub requires redis. Please enable.");
    }

    String completeKey = makeCompletedKey(description, messageId);
    if (messageComplete(completeKey, description.getMessagePayload())) {
      // Acknowledge duplicate messages but don't process them
      acknowledger.ack();
      registry.counter(getDuplicateMetricId(description)).increment();
      return;
    }

    String processingKey = makeProcessingKey(description, messageId);
    if (tryAck(processingKey, description.getAckDeadlineMillis(), acknowledger, identifier)) {
      processEvent(description);
      setMessageComplete(completeKey, description.getMessagePayload(), description.getRetentionDeadlineMillis());
      registry.counter(getProcessedMetricId(description)).increment();
    }
  }

  private boolean tryAck(String messageKey,
                         Long ackDeadlineMillis,
                         MessageAcknowledger acknowledger,
                         String identifier) {
    if (!acquireMessageLock(messageKey, identifier, ackDeadlineMillis)) {
      acknowledger.nack();
      return false;
    } else {
      acknowledger.ack();
      return true;
    }
  }

  private Boolean acquireMessageLock(String messageKey, String identifier, Long ackDeadlineMillis) {
    String response = redisClientDelegate.withCommandsClient(c -> {
      return c.set(messageKey, identifier, SET_IF_NOT_EXIST, SET_EXPIRE_TIME_MILLIS, ackDeadlineMillis);
    });
    return SUCCESS.equals(response);
  }

  private void setMessageHandled(String messageKey, String identifier, Long retentionDeadlineMillis) {
    redisClientDelegate.withCommandsClient(c -> {
      c.psetex(messageKey, retentionDeadlineMillis, identifier);
    });
  }

  private void setMessageComplete(String messageKey, String payload, Long retentionDeadlineMillis) {
    redisClientDelegate.withCommandsClient(c -> {
      c.psetex(messageKey, retentionDeadlineMillis, getCRC32(payload));
    });
  }

  private Boolean messageComplete(String messageKey, String value) {
    return redisClientDelegate.withCommandsClient(c -> {
      return getCRC32(value).equals(c.get(messageKey));
    });
  }

  // Todo emjburns: change key format to "{echo:pubsub:system}:%s:%s" and migrate messages
  private String makeProcessingKey(MessageDescription description, String messageId) {
    return String.format("%s:echo-pubsub:%s:%s", description.getPubsubSystem().toString(), description.getSubscriptionName(), messageId);
  }

  private String makeCompletedKey(MessageDescription description, String messageId) {
    return String.format("{echo:pubsub:completed}:%s:%s:%s", description.getPubsubSystem().toString(), description.getSubscriptionName(), messageId);
  }

  private void processEvent(MessageDescription description) {
    log.debug("Processing pubsub event with payload {}", description.getMessagePayload());
    Event event = new Event();
    Map<String, Object> content = new HashMap<>();
    Metadata details = new Metadata();

    try {
      event.setPayload(objectMapper.readValue(description.getMessagePayload(), Map.class));
    } catch (IOException e) {
      log.warn("Could not parse message payload as JSON", e);
    }

    content.put("messageDescription", description);
    details.setType(PubsubEventMonitor.PUBSUB_TRIGGER_TYPE);

    if (description.getMessageAttributes() != null) {
      details.setAttributes(description.getMessageAttributes());
    }

    event.setContent(content);
    event.setDetails(details);
    pubsubEventMonitor.processEvent(event);
  }

  /**
   * Generates a string checksum for comparing message body.
   */
  private String getCRC32(String value) {
    CRC32 checksum = new CRC32();
    checksum.update(value.getBytes());
    return Long.toString(checksum.getValue());
  }

  private Id getDuplicateMetricId(MessageDescription messageDescription) {
    return registry.createId("echo.pubsub.duplicateMessages")
      .withTag("subscription", messageDescription.getSubscriptionName())
      .withTag("pubsubSystem", messageDescription.getPubsubSystem().toString());
  }

  private Id getProcessedMetricId(MessageDescription messageDescription) {
    return registry.createId("echo.pubsub.messagesProcessed")
      .withTag("subscription", messageDescription.getSubscriptionName())
      .withTag("pubsubSystem", messageDescription.getPubsubSystem().toString());
  }
}
