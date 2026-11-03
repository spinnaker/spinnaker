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

import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Drives {@link RedisStreamsSubscriberProvider}'s reclaim and dead-letter paths against a real
 * Valkey instance, since the unit tests' mocked {@code XPENDING}/{@code XCLAIM} can't show that the
 * paging ranges and delivery counts behave the way the code assumes.
 */
class RedisStreamsReclaimIntegrationTest {
  private static GenericContainer<?> valkey;
  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  private RedisStreamsSubscriberProvider provider;

  @BeforeAll
  static void startValkey() {
    valkey =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8")).withExposedPorts(6379);
    valkey.start();
    connectionFactory = new LettuceConnectionFactory(valkey.getHost(), valkey.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void stopValkey() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
    if (valkey != null) {
      valkey.stop();
    }
  }

  @AfterEach
  void stopProvider() {
    if (provider != null) {
      provider.destroy();
    }
  }

  @Test
  void recordThatKeepsFailingIsMovedToTheDeadLetterStream() throws Exception {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMaxDeliveryAttempts(2);
    List<String> attempts = new CopyOnWriteArrayList<>();
    provider =
        newProvider(
            subscription,
            record -> {
              attempts.add(record.getId().getValue());
              throw new IllegalStateException("poison");
            });
    provider.start();
    redisTemplate.opsForStream().add(subscription.getStreamKey(), Map.of("message", "poison"));
    awaitUntil(() -> attempts.size() == 1);

    // Delivery 1 failed and was nacked; each reclaim is a further delivery, until the count hits
    // maxDeliveryAttempts and the record is dead-lettered instead.
    for (int run = 0; run < 3 && deadLetters(subscription).isEmpty(); run++) {
      Thread.sleep(50);
      provider.reclaimAbandonedRecords(subscription);
      Thread.sleep(50);
    }

    List<MapRecord<String, Object, Object>> deadLetters = deadLetters(subscription);
    assertThat(deadLetters).hasSize(1);
    assertThat(deadLetters.get(0).getValue())
        .containsEntry("message", "poison")
        .containsEntry("deadLetter.sourceStream", subscription.getStreamKey())
        .containsEntry("deadLetter.deliveryCount", "2");
    assertThat(attempts).hasSize(2);
    assertThat(
            redisTemplate
                .opsForStream()
                .pending(subscription.getStreamKey(), subscription.getConsumerGroup())
                .getTotalPendingMessages())
        .isZero();
  }

  @Test
  void reclaimPagesThroughMoreThanOnePageOfPendingRecords() throws Exception {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription();
    subscription.setMaxNumberOfMessages(200);
    List<String> handled = new CopyOnWriteArrayList<>();
    provider = newProvider(subscription, record -> handled.add(record.getId().getValue()));
    provider.registerSubscription(subscription);

    for (int i = 0; i < 150; i++) {
      redisTemplate.opsForStream().add(subscription.getStreamKey(), Map.of("message", "m" + i));
    }
    // A dead instance read them all and never acked.
    List<MapRecord<String, Object, Object>> delivered =
        redisTemplate
            .opsForStream()
            .read(
                Consumer.from(subscription.getConsumerGroup(), "dead-instance"),
                StreamReadOptions.empty().count(150),
                StreamOffset.create(subscription.getStreamKey(), ReadOffset.lastConsumed()));
    assertThat(delivered).hasSize(150);
    Thread.sleep(50);

    provider.reclaimAbandonedRecords(subscription);

    awaitUntil(() -> handled.size() == 150);
    assertThat(handled).doesNotHaveDuplicates();
  }

  private RedisStreamsSubscriberProvider newProvider(
      RedisPubsubProperties.RedisStreamSubscription subscription,
      RedisStreamMessageHandler handler) {
    RedisPubsubProperties properties = new RedisPubsubProperties();
    properties.setSubscriptions(List.of(subscription));
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    return new RedisStreamsSubscriberProvider(
        properties,
        new PubsubSubscribers(),
        s -> handler,
        new DefaultRedisStreamMessageAcknowledger(redisTemplate, meterRegistry),
        redisTemplate,
        connectionFactory,
        meterRegistry);
  }

  private List<MapRecord<String, Object, Object>> deadLetters(
      RedisPubsubProperties.RedisStreamSubscription subscription) {
    return redisTemplate
        .opsForStream()
        .range(subscription.getDeadLetterStreamKey(), Range.unbounded());
  }

  private static RedisPubsubProperties.RedisStreamSubscription subscription() {
    String id = UUID.randomUUID().toString();
    RedisPubsubProperties.RedisStreamSubscription subscription =
        new RedisPubsubProperties.RedisStreamSubscription();
    subscription.setName("sub-" + id);
    subscription.setMinIdleTimeForClaim(Duration.ZERO);
    return subscription;
  }

  private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Timed out waiting for condition");
      }
      Thread.sleep(50);
    }
  }
}
