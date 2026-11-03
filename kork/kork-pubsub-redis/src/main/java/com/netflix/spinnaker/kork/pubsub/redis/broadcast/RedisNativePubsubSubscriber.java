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

import com.netflix.spinnaker.kork.pubsub.model.BroadcastMessage;
import com.netflix.spinnaker.kork.pubsub.model.DeliveryGuarantee;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastMessageHandler;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastSubscriber;
import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.redis.inbound.RedisInboundChannelAdapter;

/**
 * Listens on a Redis Pub/Sub channel via Spring Integration's {@link RedisInboundChannelAdapter}.
 * The adapter's output channel is a private {@link DirectChannel} whose only subscriber bridges
 * every received message into the registered {@link PubsubBroadcastMessageHandler}. There is no
 * ack/nack: a handler exception is logged and counted, never retried - there is nothing to
 * redeliver.
 *
 * <p>Implements both the broadcast interfaces and the base {@link PubsubSubscriber}, so generic
 * tooling built against the competing-consumer contract can still discover this subscriber.
 */
@Slf4j
public class RedisNativePubsubSubscriber implements PubsubBroadcastSubscriber, PubsubSubscriber {
  private final RedisPubsubProperties.RedisBroadcastSubscription subscription;
  private final RedisInboundChannelAdapter adapter;

  public RedisNativePubsubSubscriber(
      RedisPubsubProperties.RedisBroadcastSubscription subscription,
      RedisInboundChannelAdapter adapter,
      PubsubBroadcastMessageHandler messageHandler,
      MeterRegistry meterRegistry) {
    this.subscription = subscription;
    this.adapter = adapter;

    DirectChannel outputChannel = new DirectChannel();
    outputChannel.subscribe(
        message -> {
          BroadcastMessage broadcastMessage =
              BroadcastMessage.builder()
                  .channel(subscription.getChannel())
                  .body(String.valueOf(message.getPayload()))
                  .build();
          try {
            messageHandler.handleMessage(broadcastMessage);
            meterRegistry
                .counter(
                    "pubsub.redis.broadcastProcessed",
                    List.of(Tag.of("subscription", subscription.getName())))
                .increment();
          } catch (Exception e) {
            log.error(
                "Failed to process broadcast message on channel {}", subscription.getChannel(), e);
            meterRegistry
                .counter(
                    "pubsub.redis.broadcastFailed",
                    List.of(
                        Tag.of("subscription", subscription.getName()),
                        Tag.of("exceptionClass", e.getClass().getSimpleName())))
                .increment();
          }
        });
    adapter.setOutputChannel(outputChannel);
  }

  public void start() {
    adapter.start();
  }

  public void stop() {
    adapter.stop();
  }

  @Override
  public String getPubsubSystem() {
    return RedisPubsubConfig.SYSTEM;
  }

  @Override
  public String getSubscriptionName() {
    return subscription.getName();
  }

  @Override
  public String getName() {
    return getSubscriptionName();
  }

  @Override
  public DeliveryGuarantee getDeliveryGuarantee() {
    return DeliveryGuarantee.AT_MOST_ONCE;
  }
}
