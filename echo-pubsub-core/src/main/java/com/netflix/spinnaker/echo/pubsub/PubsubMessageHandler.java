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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.events.EventPropagator;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.pubsub.model.EventCreator;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.params.SetParams;

/** Shared cache of received and handled pubsub messages to synchronize clients. */
@Slf4j
public class PubsubMessageHandler {

  private final EventPropagator eventPropagator;
  private RedisClientDelegate redisClientDelegate;
  private final Registry registry;
  private final List<EventCreator> eventCreators;

  private static final String SUCCESS = "OK";

  @Service
  public static class Factory {
    private final EventPropagator eventPropagator;
    private final RedisClientDelegate redisClientDelegate;
    private final Registry registry;

    public Factory(
        EventPropagator eventPropagator,
        Optional<RedisClientSelector> redisClientSelector,
        Registry registry) {
      this.eventPropagator = eventPropagator;
      this.redisClientDelegate =
          redisClientSelector.map(selector -> selector.primary("default")).orElse(null);
      this.registry = registry;
    }

    public PubsubMessageHandler create(EventCreator eventCreator) {
      return create(Collections.singletonList(eventCreator));
    }

    public PubsubMessageHandler create(List<EventCreator> eventCreators) {
      return new PubsubMessageHandler(
          eventPropagator, redisClientDelegate, registry, eventCreators);
    }
  }

  private PubsubMessageHandler(
      EventPropagator eventPropagator,
      RedisClientDelegate redisClientDelegate,
      Registry registry,
      List<EventCreator> eventCreators) {
    this.eventPropagator = eventPropagator;
    this.redisClientDelegate = redisClientDelegate;
    this.registry = registry;
    this.eventCreators = eventCreators;
  }

  public void handleMessage(
      MessageDescription description,
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
    if (tryAck(processingKey, description.getAckDeadlineSeconds(), acknowledger, identifier)) {
      for (EventCreator eventCreator : eventCreators) {
        Event event = eventCreator.createEvent(description);
        eventPropagator.processEvent(event);
      }
      setMessageComplete(
          completeKey, description.getMessagePayload(), description.getRetentionDeadlineSeconds());
      registry.counter(getProcessedMetricId(description)).increment();
    }
  }

  private boolean tryAck(
      String messageKey,
      Integer ackDeadlineSeconds,
      MessageAcknowledger acknowledger,
      String identifier) {
    if (!acquireMessageLock(messageKey, identifier, ackDeadlineSeconds)) {
      acknowledger.nack();
      return false;
    } else {
      acknowledger.ack();
      return true;
    }
  }

  private Boolean acquireMessageLock(
      String messageKey, String identifier, Integer ackDeadlineSeconds) {
    String response =
        redisClientDelegate.withCommandsClient(
            c -> {
              return c.set(
                  messageKey, identifier, SetParams.setParams().nx().ex(ackDeadlineSeconds));
            });
    return SUCCESS.equals(response);
  }

  private void setMessageHandled(
      String messageKey, String identifier, Integer retentionDeadlineSeconds) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.setex(messageKey, retentionDeadlineSeconds, identifier);
        });
  }

  private void setMessageComplete(
      String messageKey, String payload, Integer retentionDeadlineSeconds) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.setex(messageKey, retentionDeadlineSeconds, getCRC32(payload));
        });
  }

  private Boolean messageComplete(String messageKey, String value) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return getCRC32(value).equals(c.get(messageKey));
        });
  }

  // Todo emjburns: change key format to "{echo:pubsub:system}:%s:%s" and migrate messages
  private String makeProcessingKey(MessageDescription description, String messageId) {
    return String.format(
        "%s:echo-pubsub:%s:%s",
        description.getPubsubSystem().toString(), description.getSubscriptionName(), messageId);
  }

  private String makeCompletedKey(MessageDescription description, String messageId) {
    return String.format(
        "{echo:pubsub:completed}:%s:%s:%s",
        description.getPubsubSystem().toString(), description.getSubscriptionName(), messageId);
  }

  /** Generates a string checksum for comparing message body. */
  private String getCRC32(String value) {
    CRC32 checksum = new CRC32();
    checksum.update(value.getBytes());
    return Long.toString(checksum.getValue());
  }

  private Id getDuplicateMetricId(MessageDescription messageDescription) {
    return registry
        .createId("echo.pubsub.duplicateMessages")
        .withTag("subscription", messageDescription.getSubscriptionName())
        .withTag("pubsubSystem", messageDescription.getPubsubSystem().toString());
  }

  private Id getProcessedMetricId(MessageDescription messageDescription) {
    return registry
        .createId("echo.pubsub.messagesProcessed")
        .withTag("subscription", messageDescription.getSubscriptionName())
        .withTag("pubsubSystem", messageDescription.getPubsubSystem().toString());
  }
}
