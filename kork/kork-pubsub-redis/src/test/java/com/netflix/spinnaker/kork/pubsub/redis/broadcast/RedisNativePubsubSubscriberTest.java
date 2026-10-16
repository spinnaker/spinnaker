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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.netflix.spinnaker.kork.pubsub.model.BroadcastMessage;
import com.netflix.spinnaker.kork.pubsub.model.DeliveryGuarantee;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastMessageHandler;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.redis.inbound.RedisInboundChannelAdapter;
import org.springframework.messaging.support.MessageBuilder;

class RedisNativePubsubSubscriberTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  @DisplayName("an incoming message is converted to a BroadcastMessage and handled")
  void incomingMessageIsHandled() {
    RedisInboundChannelAdapter adapter =
        new RedisInboundChannelAdapter(
            mock(org.springframework.data.redis.connection.RedisConnectionFactory.class));
    PubsubBroadcastMessageHandler handler = mock(PubsubBroadcastMessageHandler.class);
    RedisPubsubProperties.RedisBroadcastSubscription subscription = subscription();

    RedisNativePubsubSubscriber subscriber =
        new RedisNativePubsubSubscriber(subscription, adapter, handler, meterRegistry);

    assertThat(subscriber.getPubsubSystem()).isEqualTo(RedisPubsubConfig.SYSTEM);
    assertThat(subscriber.getSubscriptionName()).isEqualTo("my-broadcast");
    assertThat(subscriber.getDeliveryGuarantee()).isEqualTo(DeliveryGuarantee.AT_MOST_ONCE);

    // simulate the adapter delivering a message, without needing a real Redis connection
    ((DirectChannel) adapter.getOutputChannel()).send(MessageBuilder.withPayload("hello").build());

    org.mockito.Mockito.verify(handler)
        .handleMessage(
            org.mockito.ArgumentMatchers.argThat(
                (BroadcastMessage m) ->
                    m.getChannel().equals("my-channel") && m.getBody().equals("hello")));
    assertThat(meterRegistry.get("pubsub.redis.broadcastProcessed").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("a handler exception is counted, not thrown")
  void handlerExceptionIsCountedNotThrown() {
    RedisInboundChannelAdapter adapter =
        new RedisInboundChannelAdapter(
            mock(org.springframework.data.redis.connection.RedisConnectionFactory.class));
    PubsubBroadcastMessageHandler handler = spy(PubsubBroadcastMessageHandler.class);
    doThrow(new RuntimeException("boom")).when(handler).handleMessage(any());

    RedisNativePubsubSubscriber subscriber =
        new RedisNativePubsubSubscriber(subscription(), adapter, handler, meterRegistry);

    ((DirectChannel) adapter.getOutputChannel()).send(MessageBuilder.withPayload("hello").build());

    assertThat(meterRegistry.get("pubsub.redis.broadcastFailed").counter().count()).isEqualTo(1.0);
  }

  private RedisPubsubProperties.RedisBroadcastSubscription subscription() {
    RedisPubsubProperties.RedisBroadcastSubscription subscription =
        new RedisPubsubProperties.RedisBroadcastSubscription();
    subscription.setName("my-broadcast");
    subscription.setChannel("my-channel");
    return subscription;
  }
}
