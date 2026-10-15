/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.pubsub.redis;

import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;

/**
 * Transport lifecycle and pending-message recovery; application concurrency belongs to the handler.
 */
class RedisMessageSubscription implements MessageSubscription {
  private final StringRedisTemplate redis;
  private final String streamKey;
  private final String group;
  private final String name;
  private final String consumerName;
  private final boolean broadcast;
  private final Consumer<PubsubMessage> handler;
  private final Consumer<Throwable> errorHandler;
  private final PubsubSubscribers subscribers;
  private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
  private String pendingCursor;
  private volatile boolean closed;
  private boolean groupInitialized;

  RedisMessageSubscription(
      StringRedisTemplate redis,
      String streamKey,
      String group,
      String name,
      int batchSize,
      Consumer<PubsubMessage> handler,
      Consumer<Throwable> errorHandler,
      PubsubSubscribers subscribers) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.redis = redis;
    this.streamKey = streamKey;
    this.broadcast = group == null;
    this.consumerName = name + "-" + UUID.randomUUID();
    this.group = broadcast ? "broadcast-" + consumerName : group;
    this.name = name;
    this.handler = handler;
    this.errorHandler = errorHandler;
    this.subscribers = subscribers;
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("redis-pubsub-" + name + "-");
    executor.setDaemon(true);
    container =
        StreamMessageListenerContainer.create(
            redis.getConnectionFactory(),
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .batchSize(batchSize)
                .executor(executor)
                .build());
  }

  @Override
  public String getPubsubSystem() {
    return "redis";
  }

  @Override
  public String getSubscriptionName() {
    return group;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isRunning() {
    return container.isRunning();
  }

  @Override
  public synchronized void start() {
    if (closed) {
      throw new IllegalStateException("Subscription is closed");
    }
    if (container.isRunning()) {
      return;
    }
    ensureGroup();
    groupInitialized = true;
    container.register(
        StreamReadRequest.builder(StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
            .consumer(
                org.springframework.data.redis.connection.stream.Consumer.from(group, consumerName))
            .autoAcknowledge(false)
            .cancelOnError(t -> false)
            .errorHandler(
                error -> {
                  if (!closed && container.isRunning()) {
                    errorHandler.accept(error);
                  }
                })
            .build(),
        this::deliver);
    container.start();
  }

  private void ensureGroup() {
    try {
      redis.opsForStream().createGroup(streamKey, ReadOffset.from(broadcast ? "$" : "0"), group);
    } catch (RuntimeException e) {
      if (!isExistingGroup(e)) {
        throw e;
      }
    }
  }

  private boolean isExistingGroup(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
        return true;
      }
    }
    return false;
  }

  private void deliver(MapRecord<String, ?, ?> record) {
    if (!container.isRunning()) {
      return;
    }
    Map<String, String> attributes = new HashMap<>();
    record.getValue().forEach((key, value) -> attributes.put(key.toString(), value.toString()));
    String payload = attributes.remove(RedisMessageChannel.PAYLOAD_FIELD);
    PubsubMessage message =
        new PubsubMessage(record.getId().getValue(), payload == null ? "" : payload, attributes);
    try {
      handler.accept(message);
    } catch (Exception e) {
      // Leave pending. Reclaim retries only after the configured idle threshold, avoiding hot
      // loops.
      if (!closed && container.isRunning()) {
        errorHandler.accept(e);
      }
    }
  }

  @Override
  public synchronized void stop() {
    container.stop();
  }

  @Override
  public void ack(PubsubMessage message) {
    redis.opsForStream().acknowledge(streamKey, group, RecordId.of(message.id()));
  }

  @Override
  public void nack(PubsubMessage message) {
    // An unacknowledged record remains in the PEL, available for a later reclaim.
  }

  @Override
  public synchronized long reclaim(Duration minIdle, int limit) {
    if (!container.isRunning()) {
      return 0;
    }
    // XPENDING uses a literal "(" prefix for an exclusive ID; the client range builder
    // does not encode exclusive string bounds for this command.
    Range<String> range =
        pendingCursor == null
            ? Range.unbounded()
            : Range.rightUnbounded(Range.Bound.inclusive("(" + pendingCursor));
    PendingMessages pending = redis.opsForStream().pending(streamKey, group, range, limit);
    pendingCursor =
        pending.size() < limit ? null : pending.get(pending.size() - 1).getId().getValue();
    RecordId[] abandoned =
        pending.stream()
            .filter(message -> message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0)
            .map(PendingMessage::getId)
            .toArray(RecordId[]::new);
    if (abandoned.length == 0) {
      return 0;
    }
    var claimed = redis.opsForStream().claim(streamKey, group, consumerName, minIdle, abandoned);
    claimed.forEach(this::deliver);
    return claimed.size();
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    stop();
    if (broadcast && groupInitialized) {
      redis.opsForStream().destroyGroup(streamKey, group);
    }
    subscribers.remove(this);
  }
}
