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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@SuppressWarnings("unchecked")
class RedisStreamsPublisherTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  @DisplayName("publish adds a record with the message field and approximate MAXLEN trimming")
  void publishAddsRecordWithTrimming() {
    when(redisTemplate.opsForStream()).thenReturn(streamOps);
    // StreamRecords.newRecord().ofMap(...).withStreamKey(...) has static type MapRecord, so
    // RedisStreamsPublisher's call resolves to the add(MapRecord, XAddOptions) default overload,
    // not add(Record, XAddOptions) - Java picks the most specific applicable overload.
    when(streamOps.add(any(MapRecord.class), any(XAddOptions.class)))
        .thenReturn(RecordId.of("1-1"));

    RedisPubsubProperties.RedisStreamSubscription subscription =
        new RedisPubsubProperties.RedisStreamSubscription();
    subscription.setName("my-subscription");
    subscription.setStreamMaxLength(500);

    RedisStreamsPublisher publisher =
        new RedisStreamsPublisher(subscription, redisTemplate, meterRegistry);

    assertThat(publisher.getPubsubSystem()).isEqualTo(RedisPubsubConfig.SYSTEM);
    assertThat(publisher.getName()).isEqualTo("my-subscription");

    publisher.publish("hello", Map.of("attr", "value"));

    ArgumentCaptor<MapRecord<String, ?, ?>> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
    ArgumentCaptor<XAddOptions> optionsCaptor = ArgumentCaptor.forClass(XAddOptions.class);
    verify(streamOps).add(recordCaptor.capture(), optionsCaptor.capture());

    MapRecord<String, ?, ?> record = recordCaptor.getValue();
    Map<String, String> fields = (Map<String, String>) record.getValue();
    assertThat(fields).containsEntry(RedisStreamsPublisher.MESSAGE_FIELD, "hello");
    assertThat(fields).containsEntry("attr", "value");

    XAddOptions options = optionsCaptor.getValue();
    assertThat(options.hasMaxlen()).isTrue();
    assertThat(options.getMaxlen()).isEqualTo(500L);
    assertThat(options.isApproximateTrimming()).isTrue();

    assertThat(meterRegistry.get("pubsub.redis.published").counter().count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("publish failures are counted, not thrown")
  void publishFailureIsCountedNotThrown() {
    when(redisTemplate.opsForStream()).thenReturn(streamOps);
    when(streamOps.add(any(MapRecord.class), any(XAddOptions.class)))
        .thenThrow(new RuntimeException("boom"));

    RedisPubsubProperties.RedisStreamSubscription subscription =
        new RedisPubsubProperties.RedisStreamSubscription();
    subscription.setName("my-subscription");

    RedisStreamsPublisher publisher =
        new RedisStreamsPublisher(subscription, redisTemplate, meterRegistry);

    publisher.publish("hello", Collections.emptyMap());

    assertThat(meterRegistry.get("pubsub.redis.publishFailed").counter().count()).isEqualTo(1.0);
  }
}
