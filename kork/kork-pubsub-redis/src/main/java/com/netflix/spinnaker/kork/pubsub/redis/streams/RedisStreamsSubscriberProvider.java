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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.stereotype.Component;

/**
 * Starts one {@link StreamMessageListenerContainer} per configured stream subscription, each with
 * its own worker pool, so a slow handler on one subscription doesn't starve another. Delivery
 * within a subscription is exactly-once-across-the-fleet: every instance runs this provider and
 * joins the same consumer group, so Redis delivers each record to only one of them.
 *
 * <p>An instance never holds more than {@code maxNumberOfMessages} records per subscription: the
 * container's polling thread blocks while every worker is busy, so records are left in the stream
 * for other instances rather than queued here. Records that keep failing are dead-lettered after
 * {@code maxDeliveryAttempts} deliveries.
 */
@Component
@ConditionalOnProperty({"pubsub.enabled", "pubsub.redis.enabled"})
public class RedisStreamsSubscriberProvider implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(RedisStreamsSubscriberProvider.class);

  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  private static final int RECLAIM_PAGE_SIZE = 100;

  /** Bounds the XPENDING work of one reclaim run; the scan resumes where it stopped next run. */
  private static final int RECLAIM_MAX_RECORDS_PER_RUN = 1000;

  private static final String NO_GROUP_ERROR = "NOGROUP";
  private static final String BUSY_GROUP_ERROR = "BUSYGROUP";

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

  /** One permit per worker; a record is only dispatched once a worker is free to take it. */
  private final Map<String, Semaphore> workerPermits = new ConcurrentHashMap<>();

  /** Records this instance has dispatched and not yet finished, which reclaim must leave alone. */
  private final Map<String, Set<RecordId>> inFlight = new ConcurrentHashMap<>();

  private final Map<String, AtomicLong> streamLengths = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> pendingCounts = new ConcurrentHashMap<>();

  /** Where the last reclaim run stopped scanning the pending list, per subscription. */
  private final Map<String, String> reclaimCursors = new ConcurrentHashMap<>();

  private final List<StreamMessageListenerContainer<String, MapRecord<String, String, String>>>
      containers = new ArrayList<>();
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

    List<PubsubSubscriber> subscribers = new ArrayList<>();
    properties
        .getSubscriptions()
        .forEach(
            subscription -> {
              registerSubscription(subscription);
              containers.add(newContainer(subscription));
              subscribers.add(new RedisStreamsSubscriber(subscription));
            });

    pubsubSubscribers.putAll(subscribers);
    containers.forEach(StreamMessageListenerContainer::start);

    long reclaimIntervalSeconds =
        properties.getSubscriptions().stream()
            .mapToLong(RedisPubsubProperties.RedisStreamSubscription::getReclaimIntervalSeconds)
            .min()
            .getAsLong();
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
   * One container per subscription because the container's batch size applies to every request it
   * hosts, and each subscription sizes its batch to its own worker pool.
   */
  private StreamMessageListenerContainer<String, MapRecord<String, String, String>> newContainer(
      RedisPubsubProperties.RedisStreamSubscription subscription) {
    String name = subscription.getName();
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
        StreamMessageListenerContainer.create(
            redisConnectionFactory,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(POLL_TIMEOUT)
                .batchSize(subscription.getMaxNumberOfMessages())
                .executor(new SimpleAsyncTaskExecutor("pubsub-redis-dispatcher-" + name + "-"))
                .build());
    container.register(
        StreamReadRequest.builder(
                StreamOffset.create(subscription.getStreamKey(), ReadOffset.lastConsumed()))
            .consumer(Consumer.from(subscription.getConsumerGroup(), consumerName))
            .autoAcknowledge(false)
            // Never cancel the subscription on poll errors: a transient redis hiccup must not
            // permanently stop this instance from consuming.
            .cancelOnError(t -> false)
            .errorHandler(t -> handlePollError(subscription, t))
            .build(),
        record -> dispatch(name, record));
    return container;
  }

  @VisibleForTesting
  void handlePollError(RedisPubsubProperties.RedisStreamSubscription subscription, Throwable t) {
    String name = subscription.getName();
    meterRegistry
        .counter("pubsub.redis.stream.poll.errors", List.of(Tag.of("subscription", name)))
        .increment();
    log.error("Error polling stream for subscription {}", name, t);
    if (hasRedisError(t, NO_GROUP_ERROR)) {
      // The stream or group was deleted out from under us (e.g. a FLUSHALL or failover to an
      // empty replica); polling can't succeed until the group exists again.
      log.warn("Consumer group for subscription {} no longer exists; recreating it", name);
      ensureConsumerGroup(subscription);
    }
  }

  /**
   * Registers a subscription's handler, acknowledgement info, and worker pool - the bookkeeping a
   * subscription needs independent of the shared {@link StreamMessageListenerContainer}. Split out
   * of {@link #start()} so tests can set this up directly without a real container/connection.
   */
  @VisibleForTesting
  void registerSubscription(RedisPubsubProperties.RedisStreamSubscription subscription) {
    String name = subscription.getName();
    List<Tag> tags = List.of(Tag.of("subscription", name));
    subscriptionInfoByName.put(
        name,
        RedisStreamSubscriptionInformation.builder()
            .subscription(subscription)
            .streamKey(subscription.getStreamKey())
            .consumerGroup(subscription.getConsumerGroup())
            .build());
    handlers.put(name, messageHandlerFactory.create(subscription));
    workers.put(name, newWorkerPool(subscription));
    workerPermits.put(name, new Semaphore(subscription.getMaxNumberOfMessages()));
    inFlight.put(name, ConcurrentHashMap.newKeySet());
    meterRegistry.gauge(
        "pubsub.redis.stream.length",
        tags,
        streamLengths.computeIfAbsent(name, n -> new AtomicLong()));
    meterRegistry.gauge(
        "pubsub.redis.stream.pending",
        tags,
        pendingCounts.computeIfAbsent(name, n -> new AtomicLong()));

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
    // The queue is unbounded but never grows past maxNumberOfMessages: dispatch() takes a permit
    // from workerPermits before submitting, which is what applies backpressure.
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
      if (hasRedisError(e, BUSY_GROUP_ERROR)) {
        // Another instance created it first. Expected on every startup but the first.
        log.debug(
            "Consumer group {} already exists on stream {}",
            subscription.getConsumerGroup(),
            subscription.getStreamKey());
      } else {
        // Don't fail startup over a redis outage: the poll error handler recreates a missing
        // group (NOGROUP) once redis is reachable again.
        log.warn(
            "Unable to create consumer group {} on stream {}",
            subscription.getConsumerGroup(),
            subscription.getStreamKey(),
            e);
      }
    }
  }

  @VisibleForTesting
  static boolean hasRedisError(Throwable t, String errorCode) {
    for (Throwable cause = t;
        cause != null;
        cause = cause.getCause() == cause ? null : cause.getCause()) {
      if (cause.getMessage() != null && cause.getMessage().contains(errorCode)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Hands a record to a worker, blocking until one is free. Blocking here stalls the container's
   * poll loop, which is the backpressure: undelivered records stay in the stream where any instance
   * can take them, instead of piling up in memory here (and going idle long enough to be reclaimed
   * while still queued).
   */
  @VisibleForTesting
  void dispatch(String subscriptionName, MapRecord<String, String, String> record) {
    ExecutorService worker = workers.get(subscriptionName);
    if (worker == null) {
      log.warn(
          "No worker pool for subscription {}; dropping record {}",
          subscriptionName,
          record.getId());
      return;
    }
    Set<RecordId> held = inFlight.get(subscriptionName);
    if (!held.add(record.getId())) {
      log.debug("Record {} is already in flight for {}", record.getId(), subscriptionName);
      return;
    }
    Semaphore permits = workerPermits.get(subscriptionName);
    try {
      permits.acquire();
    } catch (InterruptedException e) {
      // Shutting down. The record stays pending and is redelivered to a surviving instance.
      held.remove(record.getId());
      Thread.currentThread().interrupt();
      return;
    }
    try {
      worker.execute(
          () -> {
            try {
              processRecord(subscriptionName, record);
            } finally {
              held.remove(record.getId());
              permits.release();
            }
          });
    } catch (RejectedExecutionException e) {
      held.remove(record.getId());
      permits.release();
      log.warn(
          "Worker pool for {} is shut down; leaving record {} pending",
          subscriptionName,
          record.getId());
    }
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
    String name = subscription.getName();
    try {
      updateStreamGauges(subscription);

      Duration minIdle = subscription.getMinIdleTimeForClaim();
      Set<RecordId> held = inFlight.getOrDefault(name, Set.of());
      List<RecordId> abandoned = new ArrayList<>();
      Map<RecordId, Long> poisoned = new LinkedHashMap<>();

      String cursor = reclaimCursors.get(name);
      int scanned = 0;
      while (scanned < RECLAIM_MAX_RECORDS_PER_RUN) {
        // The pending list is ordered by id; page through it instead of re-reading the first page
        // forever, which would let a backlog of not-yet-idle records hide every abandoned one
        // behind it.
        Range<?> range =
            cursor == null
                ? Range.unbounded()
                : Range.rightUnbounded(Range.Bound.inclusive(cursor));
        PendingMessages page =
            redisTemplate
                .opsForStream()
                .pending(
                    subscription.getStreamKey(),
                    subscription.getConsumerGroup(),
                    range,
                    RECLAIM_PAGE_SIZE);
        List<PendingMessage> messages = new ArrayList<>();
        for (PendingMessage message : page) {
          if (cursor == null || !message.getId().getValue().equals(cursor)) {
            messages.add(message);
          }
        }
        if (messages.isEmpty()) {
          cursor = null;
          break;
        }
        for (PendingMessage message : messages) {
          if (held.contains(message.getId())
              || message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) <= 0) {
            continue;
          }
          if (message.getTotalDeliveryCount() >= subscription.getMaxDeliveryAttempts()) {
            poisoned.put(message.getId(), message.getTotalDeliveryCount());
          } else {
            abandoned.add(message.getId());
          }
        }
        scanned += messages.size();
        cursor = messages.get(messages.size() - 1).getId().getValue();
        if (messages.size() < RECLAIM_PAGE_SIZE) {
          cursor = null;
          break;
        }
      }
      if (cursor == null) {
        reclaimCursors.remove(name);
      } else {
        reclaimCursors.put(name, cursor);
      }

      deadLetter(subscription, poisoned, minIdle);
      reclaim(subscription, abandoned, minIdle);
    } catch (Exception e) {
      log.warn("Unable to scan for abandoned records for subscription {}", name, e);
    }
  }

  private void reclaim(
      RedisPubsubProperties.RedisStreamSubscription subscription,
      List<RecordId> abandoned,
      Duration minIdle) {
    if (abandoned.isEmpty()) {
      return;
    }
    String name = subscription.getName();
    List<MapRecord<String, String, String>> claimed =
        redisTemplate
            .<String, String>opsForStream()
            .claim(
                subscription.getStreamKey(),
                subscription.getConsumerGroup(),
                consumerName,
                minIdle,
                abandoned.toArray(RecordId[]::new));
    // XCLAIM skips records another consumer claimed or acked since we scanned, so count what we
    // actually got.
    log.warn(
        "Reclaimed {} records abandoned by dead or stalled consumers for subscription {}",
        claimed.size(),
        name);
    meterRegistry
        .counter("pubsub.redis.reclaimed", List.of(Tag.of("subscription", name)))
        .increment(claimed.size());
    claimed.forEach(record -> dispatch(name, record));
  }

  /**
   * Moves records that exhausted {@code maxDeliveryAttempts} to the dead-letter stream and
   * acknowledges them, so one unprocessable record can't be redelivered forever. The record is
   * acknowledged only after the dead-letter write succeeded; otherwise it stays pending and the
   * next reclaim run retries.
   */
  private void deadLetter(
      RedisPubsubProperties.RedisStreamSubscription subscription,
      Map<RecordId, Long> poisoned,
      Duration minIdle) {
    if (poisoned.isEmpty()) {
      return;
    }
    String name = subscription.getName();
    List<MapRecord<String, String, String>> claimed =
        redisTemplate
            .<String, String>opsForStream()
            .claim(
                subscription.getStreamKey(),
                subscription.getConsumerGroup(),
                consumerName,
                minIdle,
                poisoned.keySet().toArray(RecordId[]::new));
    for (MapRecord<String, String, String> record : claimed) {
      try {
        Map<String, String> fields = new HashMap<>(record.getValue());
        fields.put("deadLetter.sourceStream", subscription.getStreamKey());
        fields.put("deadLetter.sourceRecordId", record.getId().getValue());
        fields.put("deadLetter.deliveryCount", String.valueOf(poisoned.get(record.getId())));
        redisTemplate
            .opsForStream()
            .add(
                StreamRecords.newRecord()
                    .ofMap(fields)
                    .withStreamKey(subscription.getDeadLetterStreamKey()),
                XAddOptions.maxlen(subscription.getStreamMaxLength()).approximateTrimming(true));
        redisTemplate
            .opsForStream()
            .acknowledge(
                subscription.getStreamKey(), subscription.getConsumerGroup(), record.getId());
        log.error(
            "Record {} on subscription {} failed {} deliveries; moved it to dead-letter stream {}",
            record.getId(),
            name,
            poisoned.get(record.getId()),
            subscription.getDeadLetterStreamKey());
        meterRegistry
            .counter("pubsub.redis.deadLettered", List.of(Tag.of("subscription", name)))
            .increment();
      } catch (Exception e) {
        log.error(
            "Unable to dead-letter record {} for subscription {}; will retry",
            record.getId(),
            name,
            e);
      }
    }
  }

  private void updateStreamGauges(RedisPubsubProperties.RedisStreamSubscription subscription) {
    try {
      Long length = redisTemplate.opsForStream().size(subscription.getStreamKey());
      PendingMessagesSummary summary =
          redisTemplate
              .opsForStream()
              .pending(subscription.getStreamKey(), subscription.getConsumerGroup());
      if (length != null) {
        streamLengths.computeIfAbsent(subscription.getName(), n -> new AtomicLong()).set(length);
      }
      if (summary != null) {
        pendingCounts
            .computeIfAbsent(subscription.getName(), n -> new AtomicLong())
            .set(summary.getTotalPendingMessages());
      }
    } catch (Exception e) {
      log.debug("Unable to read stream stats for subscription {}", subscription.getName(), e);
    }
  }

  @Override
  public void destroy() {
    // Stop producing work before stopping the workers that consume it.
    if (reclaimScheduler != null) {
      reclaimScheduler.shutdownNow();
    }
    containers.forEach(StreamMessageListenerContainer::stop);
    workers.values().forEach(ExecutorService::shutdown);
    long deadline = System.nanoTime() + SHUTDOWN_TIMEOUT.toNanos();
    for (Map.Entry<String, ExecutorService> worker : workers.entrySet()) {
      try {
        if (!worker
            .getValue()
            .awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
          // Unacked records stay pending and another instance reclaims them.
          log.warn(
              "Handlers for subscription {} still running at shutdown; interrupting",
              worker.getKey());
          worker.getValue().shutdownNow();
        }
      } catch (InterruptedException e) {
        worker.getValue().shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
