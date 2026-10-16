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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
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

  @Test
  @DisplayName("records this instance is still processing are not reclaimed")
  void inFlightRecordsAreNotReclaimed() throws Exception {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMinIdleTimeForClaim(Duration.ofMinutes(5));
    CountDownLatch handlerEntered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(messageHandlerFactory.create(subscription))
        .thenReturn(
            record -> {
              handlerEntered.countDown();
              await(release);
            });
    provider.registerSubscription(subscription);
    StreamOperations<String, Object, Object> streamOps = streamOps();
    when(streamOps.pending(eq("my-stream"), eq("my-group"), any(), any(Long.class)))
        .thenReturn(
            new PendingMessages(
                "my-group",
                Range.unbounded(),
                List.of(pendingMessage("1-1", Duration.ofMinutes(10)))));

    provider.dispatch("my-subscription", record());
    assertThat(handlerEntered.await(5, TimeUnit.SECONDS)).isTrue();
    provider.reclaimAbandonedRecords(subscription);

    verify(streamOps, never()).claim(any(), any(), any(), any(), any(RecordId.class));
    release.countDown();
  }

  @Test
  @DisplayName("reclaimed counter counts records actually claimed, not candidates")
  void reclaimedCounterCountsClaimedRecords() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    provider.registerSubscription(subscription);
    StreamOperations<String, Object, Object> streamOps = streamOps();
    when(streamOps.pending(eq("my-stream"), eq("my-group"), any(), any(Long.class)))
        .thenReturn(
            new PendingMessages(
                "my-group",
                Range.unbounded(),
                List.of(pendingMessage("1-1", Duration.ofMinutes(10)))));
    // Another consumer claimed it between the scan and our XCLAIM.
    when(streamOps.claim(eq("my-stream"), eq("my-group"), any(), any(), any(RecordId.class)))
        .thenReturn(List.of());

    provider.reclaimAbandonedRecords(subscription);

    assertThat(
            meterRegistry
                .counter("pubsub.redis.reclaimed", "subscription", "my-subscription")
                .count())
        .isZero();
  }

  @Test
  @DisplayName("abandoned records behind a page of fresh pending records are still found")
  void reclaimScansPastTheFirstPage() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    provider.registerSubscription(subscription);
    StreamOperations<String, Object, Object> streamOps = streamOps();

    List<PendingMessage> firstPage = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      firstPage.add(pendingMessage(i + "-1", Duration.ofSeconds(1)));
    }
    PendingMessages page1 = new PendingMessages("my-group", Range.unbounded(), firstPage);
    PendingMessages page2 =
        new PendingMessages(
            "my-group",
            Range.unbounded(),
            List.of(
                pendingMessage("100-1", Duration.ofSeconds(1)),
                pendingMessage("101-1", Duration.ofMinutes(10))));
    when(streamOps.pending(eq("my-stream"), eq("my-group"), any(), any(Long.class)))
        .thenReturn(page1, page2);
    when(streamOps.claim(eq("my-stream"), eq("my-group"), any(), any(), any(RecordId.class)))
        .thenReturn(List.of());

    provider.reclaimAbandonedRecords(subscription);

    verify(streamOps)
        .claim(eq("my-stream"), eq("my-group"), any(), any(), eq(RecordId.of("101-1")));
  }

  @Test
  @DisplayName("a record that exhausted its delivery attempts is dead-lettered and acked")
  void poisonRecordIsDeadLettered() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMaxDeliveryAttempts(3);
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    provider.registerSubscription(subscription);
    StreamOperations<String, Object, Object> streamOps = streamOps();
    when(streamOps.pending(eq("my-stream"), eq("my-group"), any(), any(Long.class)))
        .thenReturn(
            new PendingMessages(
                "my-group",
                Range.unbounded(),
                List.of(pendingMessage("1-1", Duration.ofMinutes(10), 3L))));
    when(streamOps.claim(eq("my-stream"), eq("my-group"), any(), any(), any(RecordId.class)))
        .thenAnswer(invocation -> List.of(record()));

    provider.reclaimAbandonedRecords(subscription);

    ArgumentCaptor<MapRecord> deadLetter = ArgumentCaptor.forClass(MapRecord.class);
    verify(streamOps).add(deadLetter.capture(), any(XAddOptions.class));
    assertThat(deadLetter.getValue().getStream()).isEqualTo("my-stream.dead-letter");
    assertThat((Map<String, String>) deadLetter.getValue().getValue())
        .containsEntry("message", "hello")
        .containsEntry("deadLetter.sourceRecordId", "1-1")
        .containsEntry("deadLetter.deliveryCount", "3");
    verify(streamOps).acknowledge("my-stream", "my-group", RecordId.of("1-1"));
    assertThat(
            meterRegistry
                .counter("pubsub.redis.deadLettered", "subscription", "my-subscription")
                .count())
        .isEqualTo(1);
    // It must not be redelivered to a handler as well.
    verify(messageAcknowledger, never()).ack(any(), any());
  }

  @Test
  @DisplayName("a record whose dead-letter write fails stays pending for the next run")
  void poisonRecordStaysPendingIfDeadLetterFails() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMaxDeliveryAttempts(3);
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    provider.registerSubscription(subscription);
    StreamOperations<String, Object, Object> streamOps = streamOps();
    when(streamOps.pending(eq("my-stream"), eq("my-group"), any(), any(Long.class)))
        .thenReturn(
            new PendingMessages(
                "my-group",
                Range.unbounded(),
                List.of(pendingMessage("1-1", Duration.ofMinutes(10), 3L))));
    when(streamOps.claim(eq("my-stream"), eq("my-group"), any(), any(), any(RecordId.class)))
        .thenAnswer(invocation -> List.of(record()));
    when(streamOps.add(any(), any(XAddOptions.class)))
        .thenThrow(new RuntimeException("redis down"));

    provider.reclaimAbandonedRecords(subscription);

    verify(streamOps, never())
        .acknowledge(any(String.class), any(String.class), any(RecordId[].class));
  }

  @Test
  @DisplayName("dispatch blocks while every worker is busy instead of queueing records")
  void dispatchAppliesBackpressure() throws Exception {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMaxNumberOfMessages(1);
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger handled = new AtomicInteger();
    when(messageHandlerFactory.create(subscription))
        .thenReturn(
            record -> {
              handled.incrementAndGet();
              firstEntered.countDown();
              await(release);
            });
    provider.registerSubscription(subscription);

    provider.dispatch("my-subscription", record("1-1"));
    assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> second = caller.submit(() -> provider.dispatch("my-subscription", record("2-1")));
      assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      assertThat(handled).hasValue(1);

      release.countDown();
      second.get(5, TimeUnit.SECONDS);
    } finally {
      caller.shutdownNow();
    }
  }

  @Test
  @DisplayName("a NOGROUP poll error recreates the consumer group")
  void noGroupPollErrorRecreatesTheGroup() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    StreamOperations<String, Object, Object> streamOps = streamOps();

    provider.handlePollError(
        subscription,
        new RedisSystemException(
            "Error in execution", new RuntimeException("NOGROUP No such consumer group")));

    verify(streamOps).createGroup("my-stream", ReadOffset.from("0"), "my-group");
    assertThat(
            meterRegistry
                .counter("pubsub.redis.stream.poll.errors", "subscription", "my-subscription")
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("other poll errors do not touch the consumer group")
  void otherPollErrorsDoNotRecreateTheGroup() {
    StreamOperations<String, Object, Object> streamOps = streamOps();

    provider.handlePollError(subscription(), new RuntimeException("connection reset"));

    verify(streamOps, never()).createGroup(any(), any(ReadOffset.class), any());
  }

  @Test
  @DisplayName("an existing consumer group (BUSYGROUP) is not an error")
  void busyGroupIsExpected() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    when(messageHandlerFactory.create(subscription))
        .thenReturn(mock(RedisStreamMessageHandler.class));
    StreamOperations<String, Object, Object> streamOps = streamOps();
    when(streamOps.createGroup(any(), any(ReadOffset.class), any()))
        .thenThrow(
            new RedisSystemException(
                "Error in execution",
                new RuntimeException("BUSYGROUP Consumer Group name already exists")));

    assertThatCode(() -> provider.registerSubscription(subscription)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("destroy waits for running handlers and then releases the workers")
  void destroyWaitsForRunningHandlers() throws Exception {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    CountDownLatch entered = new CountDownLatch(1);
    AtomicBoolean finished = new AtomicBoolean();
    when(messageHandlerFactory.create(subscription))
        .thenReturn(
            record -> {
              entered.countDown();
              try {
                Thread.sleep(300);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              finished.set(true);
            });
    provider.registerSubscription(subscription);
    provider.dispatch("my-subscription", record());
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    provider.destroy();

    assertThat(finished).isTrue();
  }

  @SuppressWarnings("unchecked")
  private StreamOperations<String, Object, Object> streamOps() {
    StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    when(redisTemplate.opsForStream()).thenReturn(streamOps);
    return streamOps;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
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
    return record("1-1");
  }

  private MapRecord<String, String, String> record(String id) {
    return MapRecord.create("my-stream", Map.of("message", "hello")).withId(RecordId.of(id));
  }

  private PendingMessage pendingMessage(String recordId, Duration elapsed) {
    return pendingMessage(recordId, elapsed, 1L);
  }

  private PendingMessage pendingMessage(String recordId, Duration elapsed, long deliveryCount) {
    return new PendingMessage(
        RecordId.of(recordId), Consumer.from("my-group", "someone-else"), elapsed, deliveryCount);
  }
}
