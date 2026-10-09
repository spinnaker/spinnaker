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

import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.instance.InstanceIdentity;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageHandler;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageHandlerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.stereotype.Component;

/**
 * Starts a single shared {@link StreamMessageListenerContainer} multiplexing every configured
 * stream subscription - one worker pool per subscription, so a slow handler on one subscription
 * doesn't starve another. Delivery within a subscription is exactly-once-across-the-fleet: every
 * instance runs this provider and joins the same consumer group, so Redis delivers each record to
 * only one of them.
 */
@Component
@ConditionalOnProperty({"pubsub.enabled", "pubsub.redis.enabled"})
public class RedisStreamsSubscriberProvider implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(RedisStreamsSubscriberProvider.class);

  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
  private static final int RECLAIM_SCAN_LIMIT = 100;
  private static final int DEFAULT_BATCH_SIZE = 10;
  private static final long DEFAULT_RECLAIM_INTERVAL_SECONDS = 60;

  private final RedisPubsubProperties properties;
  private final PubsubSubscribers pubsubSubscribers;
  private final RedisStreamMessageHandlerFactory messageHandlerFactory;
  private final RedisStreamMessageAcknowledger messageAcknowledger;
  private final StringRedisTemplate redisTemplate;
  private final RedisConnectionFactory redisConnectionFactory;
  private final MeterRegistry meterRegistry;

  private final String consumerName = InstanceIdentity.getLocalInstanceId();
  private final Map<String, RedisStreamMessageHandler> handlers = new ConcurrentHashMap<>();
  private final Map<String, RedisStreamSubscriptionInformation> subscriptionInfoByName =
      new ConcurrentHashMap<>();
  private final Map<String, ExecutorService> workers = new ConcurrentHashMap<>();

  private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
  private ScheduledExecutorService reclaimScheduler;

  @Autowired
  public RedisStreamsSubscriberProvider(
      RedisPubsubProperties properties,
      PubsubSubscribers pubsubSubscribers,
      RedisStreamMessageHandlerFactory messageHandlerFactory,
      RedisStreamMessageAcknowledger messageAcknowledger,
      StringRedisTemplate redisTemplate,
      RedisConnectionFactory redisConnectionFactory,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.pubsubSubscribers = pubsubSubscribers;
    this.messageHandlerFactory = messageHandlerFactory;
    this.messageAcknowledger = messageAcknowledger;
    this.redisTemplate = redisTemplate;
    this.redisConnectionFactory = redisConnectionFactory;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void start() {
    if (properties.getSubscriptions().isEmpty()) {
      return;
    }

    int batchSize =
        properties.getSubscriptions().stream()
            .mapToInt(RedisPubsubProperties.RedisStreamSubscription::getMaxNumberOfMessages)
            .max()
            .orElse(DEFAULT_BATCH_SIZE);
    container =
        StreamMessageListenerContainer.create(
            redisConnectionFactory,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(POLL_TIMEOUT)
                .batchSize(batchSize)
                .executor(new SimpleAsyncTaskExecutor("pubsub-redis-dispatcher-"))
                .build());

    List<PubsubSubscriber> subscribers = new ArrayList<>();
    properties
        .getSubscriptions()
        .forEach(
            subscription -> {
              registerSubscription(subscription);
              String name = subscription.getName();

              container.register(
                  StreamReadRequest.builder(
                          StreamOffset.create(
                              subscription.getStreamKey(), ReadOffset.lastConsumed()))
                      .consumer(Consumer.from(subscription.getConsumerGroup(), consumerName))
                      .autoAcknowledge(false)
                      // Never cancel the subscription on poll errors: a transient redis hiccup
                      // must not permanently stop this instance from consuming.
                      .cancelOnError(t -> false)
                      .errorHandler(
                          t -> {
                            meterRegistry
                                .counter(
                                    "pubsub.redis.stream.poll.errors",
                                    List.of(Tag.of("subscription", name)))
                                .increment();
                            log.error("Error polling stream for subscription {}", name, t);
                          })
                      .build(),
                  record -> dispatch(name, record));

              subscribers.add(new RedisStreamsSubscriber(subscription));
            });

    container.start();
    pubsubSubscribers.putAll(subscribers);

    long reclaimIntervalSeconds =
        properties.getSubscriptions().stream()
            .mapToLong(RedisPubsubProperties.RedisStreamSubscription::getReclaimIntervalSeconds)
            .min()
            .orElse(DEFAULT_RECLAIM_INTERVAL_SECONDS);
    reclaimScheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "pubsub-redis-reclaim");
              thread.setDaemon(true);
              return thread;
            });
    reclaimScheduler.scheduleWithFixedDelay(
        this::reclaimAbandonedRecords,
        reclaimIntervalSeconds,
        reclaimIntervalSeconds,
        TimeUnit.SECONDS);
  }

  /**
   * Registers a subscription's handler, acknowledgement info, and worker pool - the bookkeeping a
   * subscription needs independent of the shared {@link StreamMessageListenerContainer}. Split out
   * of {@link #start()} so tests can set this up directly without a real container/connection.
   */
  @VisibleForTesting
  void registerSubscription(RedisPubsubProperties.RedisStreamSubscription subscription) {
    String name = subscription.getName();
    subscriptionInfoByName.put(
        name,
        RedisStreamSubscriptionInformation.builder()
            .subscription(subscription)
            .streamKey(subscription.getStreamKey())
            .consumerGroup(subscription.getConsumerGroup())
            .build());
    handlers.put(name, messageHandlerFactory.create(subscription));
    workers.put(name, newWorkerPool(subscription));

    ensureConsumerGroup(subscription);
    log.info(
        "Bootstrapping Redis Streams subscription {} (stream={}, consumerGroup={}, consumer={})",
        name,
        subscription.getStreamKey(),
        subscription.getConsumerGroup(),
        consumerName);
  }

  private ExecutorService newWorkerPool(
      RedisPubsubProperties.RedisStreamSubscription subscription) {
    AtomicInteger threadNumber = new AtomicInteger();
    return Executors.newFixedThreadPool(
        subscription.getMaxNumberOfMessages(),
        runnable -> {
          Thread thread =
              new Thread(
                  runnable,
                  "pubsub-redis-worker-"
                      + subscription.getName()
                      + "-"
                      + threadNumber.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        });
  }

  private void ensureConsumerGroup(RedisPubsubProperties.RedisStreamSubscription subscription) {
    try {
      // From "0" so records added before the first consumer came up are still delivered.
      redisTemplate
          .opsForStream()
          .createGroup(
              subscription.getStreamKey(), ReadOffset.from("0"), subscription.getConsumerGroup());
    } catch (Exception e) {
      // BUSYGROUP - another instance created it first. Expected on every startup but the first.
      log.debug(
          "Consumer group {} already exists on stream {}: {}",
          subscription.getConsumerGroup(),
          subscription.getStreamKey(),
          e.getMessage());
    }
  }

  private void dispatch(String subscriptionName, MapRecord<String, String, String> record) {
    ExecutorService worker = workers.get(subscriptionName);
    if (worker == null) {
      log.warn(
          "No worker pool for subscription {}; dropping record {}",
          subscriptionName,
          record.getId());
      return;
    }
    worker.execute(() -> processRecord(subscriptionName, record));
  }

  @VisibleForTesting
  void processRecord(String subscriptionName, MapRecord<String, String, String> record) {
    RedisStreamMessageHandler handler = handlers.get(subscriptionName);
    RedisStreamSubscriptionInformation info = subscriptionInfoByName.get(subscriptionName);
    Exception caught = null;
    try {
      handler.handleMessage(record);
      meterRegistry
          .counter("pubsub.redis.processed", List.of(Tag.of("subscription", subscriptionName)))
          .increment();
    } catch (Exception e) {
      log.error(
          "Failed to process record {} for subscription {}", record.getId(), subscriptionName, e);
      meterRegistry
          .counter(
              "pubsub.redis.failed",
              List.of(
                  Tag.of("subscription", subscriptionName),
                  Tag.of("exceptionClass", e.getClass().getSimpleName())))
          .increment();
      caught = e;
    }

    if (caught == null) {
      messageAcknowledger.ack(info, record);
    } else {
      messageAcknowledger.nack(info, record);
    }
  }

  /**
   * Rescues records that were delivered to a consumer (typically an instance that died or hung) but
   * never acknowledged, across every configured subscription.
   */
  private void reclaimAbandonedRecords() {
    properties.getSubscriptions().forEach(this::reclaimAbandonedRecords);
  }

  @VisibleForTesting
  void reclaimAbandonedRecords(RedisPubsubProperties.RedisStreamSubscription subscription) {
    try {
      Duration minIdle = subscription.getMinIdleTimeForClaim();
      PendingMessages pending =
          redisTemplate
              .opsForStream()
              .pending(
                  subscription.getStreamKey(),
                  subscription.getConsumerGroup(),
                  Range.unbounded(),
                  RECLAIM_SCAN_LIMIT);
      RecordId[] abandoned =
          pending.stream()
              .filter(message -> message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) > 0)
              .map(PendingMessage::getId)
              .toArray(RecordId[]::new);
      if (abandoned.length == 0) {
        return;
      }
      log.warn(
          "Reclaiming {} records abandoned by dead or stalled consumers for subscription {}",
          abandoned.length,
          subscription.getName());
      meterRegistry
          .counter(
              "pubsub.redis.reclaimed", List.of(Tag.of("subscription", subscription.getName())))
          .increment(abandoned.length);
      List<MapRecord<String, String, String>> claimed =
          redisTemplate
              .<String, String>opsForStream()
              .claim(
                  subscription.getStreamKey(),
                  subscription.getConsumerGroup(),
                  consumerName,
                  minIdle,
                  abandoned);
      claimed.forEach(record -> dispatch(subscription.getName(), record));
    } catch (Exception e) {
      log.warn(
          "Unable to scan for abandoned records for subscription {}", subscription.getName(), e);
    }
  }

  @Override
  public void destroy() {
    if (container != null) {
      container.stop();
    }
    if (reclaimScheduler != null) {
      reclaimScheduler.shutdown();
    }
    workers.values().forEach(ExecutorService::shutdown);
  }
}
