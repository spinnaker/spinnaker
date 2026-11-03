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

package com.netflix.spinnaker.kork.pubsub.redis.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class DefaultRedisStreamMessageAcknowledgerTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final DefaultRedisStreamMessageAcknowledger acknowledger =
      new DefaultRedisStreamMessageAcknowledger(redisTemplate, meterRegistry);

  @Test
  @DisplayName("ack calls XACK and increments the acked counter")
  void ackAcknowledgesAndIncrementsCounter() {
    StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    when(redisTemplate.opsForStream()).thenReturn(streamOps);

    RedisStreamSubscriptionInformation subscription = subscriptionInfo();
    MapRecord<String, String, String> record = record();

    acknowledger.ack(subscription, record);

    verify(streamOps).acknowledge("my-stream", "my-group", record.getId());
    assertThat(meterRegistry.get("pubsub.redis.acked").counter().count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("nack does not touch redis, and increments the nacked counter")
  void nackDoesNothingButIncrementsCounter() {
    StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    when(redisTemplate.opsForStream()).thenReturn(streamOps);

    RedisStreamSubscriptionInformation subscription = subscriptionInfo();
    MapRecord<String, String, String> record = record();

    acknowledger.nack(subscription, record);

    verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
    assertThat(meterRegistry.get("pubsub.redis.nacked").counter().count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("ack failures are counted, not thrown")
  void ackFailureIsCountedNotThrown() {
    StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    when(redisTemplate.opsForStream()).thenReturn(streamOps);
    when(streamOps.acknowledge(eq("my-stream"), eq("my-group"), any(RecordId.class)))
        .thenThrow(new RuntimeException("boom"));

    acknowledger.ack(subscriptionInfo(), record());

    assertThat(meterRegistry.get("pubsub.redis.ackFailed").counter().count()).isEqualTo(1.0);
  }

  private RedisStreamSubscriptionInformation subscriptionInfo() {
    RedisPubsubProperties.RedisStreamSubscription subscription =
        new RedisPubsubProperties.RedisStreamSubscription();
    subscription.setName("my-subscription");
    return RedisStreamSubscriptionInformation.builder()
        .subscription(subscription)
        .streamKey("my-stream")
        .consumerGroup("my-group")
        .build();
  }

  private MapRecord<String, String, String> record() {
    return MapRecord.create("my-stream", java.util.Map.of("message", "hello"))
        .withId(RecordId.of("1-1"));
  }
}
