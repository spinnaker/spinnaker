/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.pubsub.redis.broadcast;

import com.netflix.spinnaker.kork.pubsub.model.DeliveryGuarantee;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastPublisher;
import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.redis.outbound.RedisPublishingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Publishes to a Redis Pub/Sub channel via Spring Integration's {@link
 * RedisPublishingMessageHandler}. Implements both the broadcast interfaces and the base {@link
 * PubsubPublisher}, so generic tooling built against the competing-consumer contract can still
 * discover this publisher.
 */
@Slf4j
public class RedisNativePubsubPublisher implements PubsubBroadcastPublisher, PubsubPublisher {
  private final RedisPubsubProperties.RedisBroadcastSubscription subscription;
  private final RedisPublishingMessageHandler handler;
  private final MeterRegistry meterRegistry;

  public RedisNativePubsubPublisher(
      RedisPubsubProperties.RedisBroadcastSubscription subscription,
      RedisPublishingMessageHandler handler,
      MeterRegistry meterRegistry) {
    this.subscription = subscription;
    this.handler = handler;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public String getPubsubSystem() {
    return RedisPubsubConfig.SYSTEM;
  }

  @Override
  public String getTopicName() {
    return subscription.getChannel();
  }

  @Override
  public String getName() {
    return subscription.getName();
  }

  @Override
  public DeliveryGuarantee getDeliveryGuarantee() {
    return DeliveryGuarantee.AT_MOST_ONCE;
  }

  /**
   * Both {@link PubsubBroadcastPublisher} and {@link PubsubPublisher} declare an identical default
   * {@code publish(String)} overload, which is an ambiguous diamond for any class implementing both
   * - resolve it explicitly rather than picking one supertype's default arbitrarily.
   */
  @Override
  public void publish(String message) {
    publish(message, Collections.emptyMap());
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code attributes} are ignored: a native Redis {@code PUBLISH} carries only the raw payload
   * bytes, with no side channel for headers/attributes to travel alongside it - the same "if that
   * pubsub system supports it" escape hatch {@link PubsubPublisher#publish(String, Map)} already
   * documents.
   */
  @Override
  public void publish(String message, Map<String, String> attributes) {
    Message<String> integrationMessage = MessageBuilder.withPayload(message).build();
    try {
      handler.handleMessage(integrationMessage);
      meterRegistry
          .counter(
              "pubsub.redis.broadcastPublished",
              List.of(Tag.of("subscription", subscription.getName())))
          .increment();
    } catch (Exception e) {
      log.error("Failed to publish broadcast message to channel {}", subscription.getChannel(), e);
      meterRegistry
          .counter(
              "pubsub.redis.broadcastPublishFailed",
              List.of(
                  Tag.of("subscription", subscription.getName()),
                  Tag.of("exceptionClass", e.getClass().getSimpleName())))
          .increment();
    }
  }
}
