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
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.kork.pubsub.model.DeliveryGuarantee;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.integration.redis.outbound.RedisPublishingMessageHandler;
import org.springframework.messaging.Message;

class RedisNativePubsubPublisherTest {

  private final RedisPublishingMessageHandler handler = mock(RedisPublishingMessageHandler.class);
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  @DisplayName("publish forwards the raw payload to the handler and counts success")
  void publishForwardsPayload() {
    RedisPubsubProperties.RedisBroadcastSubscription subscription = subscription();
    RedisNativePubsubPublisher publisher =
        new RedisNativePubsubPublisher(subscription, handler, meterRegistry);

    assertThat(publisher.getPubsubSystem()).isEqualTo(RedisPubsubConfig.SYSTEM);
    assertThat(publisher.getTopicName()).isEqualTo("my-channel");
    assertThat(publisher.getDeliveryGuarantee()).isEqualTo(DeliveryGuarantee.AT_MOST_ONCE);

    publisher.publish("hello", Map.of("ignored-attribute", "value"));

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(handler).handleMessage(captor.capture());
    assertThat(captor.getValue().getPayload()).isEqualTo("hello");

    assertThat(meterRegistry.get("pubsub.redis.broadcastPublished").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("publish failures are counted, not thrown")
  void publishFailureIsCountedNotThrown() {
    doThrow(new RuntimeException("boom")).when(handler).handleMessage(any());
    RedisNativePubsubPublisher publisher =
        new RedisNativePubsubPublisher(subscription(), handler, meterRegistry);

    publisher.publish("hello");

    assertThat(meterRegistry.get("pubsub.redis.broadcastPublishFailed").counter().count())
        .isEqualTo(1.0);
  }

  private RedisPubsubProperties.RedisBroadcastSubscription subscription() {
    RedisPubsubProperties.RedisBroadcastSubscription subscription =
        new RedisPubsubProperties.RedisBroadcastSubscription();
    subscription.setName("my-broadcast");
    subscription.setChannel("my-channel");
    return subscription;
  }
}
