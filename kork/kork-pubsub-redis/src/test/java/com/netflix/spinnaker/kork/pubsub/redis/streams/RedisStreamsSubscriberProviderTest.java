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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageHandler;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageHandlerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisStreamsSubscriberProviderTest {

  private final RedisPubsubProperties properties = new RedisPubsubProperties();
  private final PubsubSubscribers pubsubSubscribers = new PubsubSubscribers();
  private final RedisStreamMessageHandlerFactory messageHandlerFactory =
      mock(RedisStreamMessageHandlerFactory.class);
  private final RedisStreamMessageAcknowledger messageAcknowledger =
      mock(RedisStreamMessageAcknowledger.class);
  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final RedisStreamsSubscriberProvider provider =
      new RedisStreamsSubscriberProvider(
          properties,
          pubsubSubscribers,
          messageHandlerFactory,
          messageAcknowledger,
          redisTemplate,
          redisConnectionFactory,
          meterRegistry);

  @Test
  @DisplayName("acknowledger acks when the handler succeeds")
  void acksWhenHandlerSucceeds() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    provider.registerSubscription(subscription);

    provider.processRecord("my-subscription", record());

    verify(messageAcknowledger, times(1)).ack(any(), any());
    verify(messageAcknowledger, never()).nack(any(), any());
  }

  @Test
  @DisplayName("acknowledger nacks when the handler throws")
  void nacksWhenHandlerThrows() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    RedisStreamMessageHandler throwyHandler = spy(RedisStreamMessageHandler.class);
    doThrow(new RuntimeException("unhappy handler")).when(throwyHandler).handleMessage(any());
    when(messageHandlerFactory.create(subscription)).thenReturn(throwyHandler);
    provider.registerSubscription(subscription);

    provider.processRecord("my-subscription", record());

    verify(messageAcknowledger, never()).ack(any(), any());
    verify(messageAcknowledger, times(1)).nack(any(), any());
  }

  @Test
  @DisplayName("only records idle longer than minIdleTimeForClaim are reclaimed")
  void onlyAbandonedRecordsAreReclaimed() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMinIdleTimeForClaim(Duration.ofMinutes(5));
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    provider.registerSubscription(subscription);

    PendingMessage abandoned = pendingMessage("1-1", Duration.ofMinutes(10));
    PendingMessage fresh = pendingMessage("2-1", Duration.ofSeconds(30));
    PendingMessages pending =
        new PendingMessages("my-group", Range.unbounded(), List.of(abandoned, fresh));

    @SuppressWarnings("unchecked")
    StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    when(redisTemplate.opsForStream()).thenReturn(streamOps);
    when(streamOps.pending(eq("my-stream"), eq("my-group"), any(), any(Long.class)))
        .thenReturn(pending);
    when(streamOps.claim(eq("my-stream"), eq("my-group"), any(), any(), any(RecordId.class)))
        .thenReturn(List.of());

    provider.reclaimAbandonedRecords(subscription);

    verify(streamOps)
        .claim(
            eq("my-stream"),
            eq("my-group"),
            any(),
            eq(Duration.ofMinutes(5)),
            eq(RecordId.of("1-1")));
  }

  private RedisPubsubProperties.RedisStreamSubscription subscription() {
    RedisPubsubProperties.RedisStreamSubscription subscription =
        new RedisPubsubProperties.RedisStreamSubscription();
    subscription.setName("my-subscription");
    subscription.setStreamKey("my-stream");
    subscription.setConsumerGroup("my-group");
    return subscription;
  }

  private MapRecord<String, String, String> record() {
    return MapRecord.create("my-stream", Map.of("message", "hello")).withId(RecordId.of("1-1"));
  }

  private PendingMessage pendingMessage(String recordId, Duration elapsed) {
    return new PendingMessage(
        RecordId.of(recordId),
        org.springframework.data.redis.connection.stream.Consumer.from("my-group", "someone-else"),
        elapsed,
        1L);
  }
}
