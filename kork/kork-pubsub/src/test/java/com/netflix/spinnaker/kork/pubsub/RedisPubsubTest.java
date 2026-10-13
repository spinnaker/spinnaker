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

package com.netflix.spinnaker.kork.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import com.netflix.spinnaker.kork.pubsub.redis.RedisMessageChannel;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisPubsubTest {
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
  private static LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redis;
  private RedisPubsub pubsub;
  private final List<MessageSubscription> subscriptions = new ArrayList<>();
  private final List<Throwable> errors = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void startRedis() {
    REDIS.start();
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
  }

  @AfterAll
  static void stopRedis() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
    REDIS.stop();
  }

  @BeforeEach
  void setUp() {
    redis = new StringRedisTemplate(connectionFactory);
    try (var connection = connectionFactory.getConnection()) {
      connection.serverCommands().flushAll();
    }
    pubsub = new RedisPubsub(redis, new PubsubPublishers(), new PubsubSubscribers());
  }

  @AfterEach
  void closeSubscriptions() {
    subscriptions.forEach(MessageSubscription::close);
    assertThat(errors).isEmpty();
  }

  @Test
  void targetedMessagesCompeteAndCannotReachUnrelatedGroups() {
    RedisMessageChannel blue = pubsub.targeted("work", "blue");
    RedisMessageChannel red = pubsub.targeted("work", "red");
    List<String> a = new CopyOnWriteArrayList<>();
    List<String> b = new CopyOnWriteArrayList<>();
    List<String> unrelated = new CopyOnWriteArrayList<>();
    subscriber(blue, a).start();
    subscriber(blue, b).start();
    subscriber(red, unrelated).start();
    for (int i = 0; i < 100; i++) {
      blue.publish("job-" + i);
    }
    await().untilAsserted(() -> assertThat(a.size() + b.size()).isEqualTo(100));
    assertThat(a).doesNotContainAnyElementsOf(b);
    red.publish("red-only");
    await().untilAsserted(() -> assertThat(unrelated).containsExactly("red-only"));
    assertThat(a).doesNotContain("red-only");
    assertThat(b).doesNotContain("red-only");
    await().untilAsserted(() -> assertThat(blue.statistics().pending()).isZero());
  }

  @Test
  void targetedMessagesSurvivePublicationBeforeSubscribersStart() {
    RedisMessageChannel channel = pubsub.targeted("work", "late");
    channel.publish("queued", Map.of("type", "safe"));
    List<String> messages = new CopyOnWriteArrayList<>();
    subscriber(channel, messages).start();
    await().untilAsserted(() -> assertThat(messages).containsExactly("queued"));
  }

  @Test
  void broadcastDeliversIndependentlyToEveryActiveInstance() {
    RedisMessageChannel channel = pubsub.broadcast("changes");
    channel.publish("before-subscribe");
    List<String> a = new CopyOnWriteArrayList<>();
    List<String> b = new CopyOnWriteArrayList<>();
    MessageSubscription first = subscriber(channel, a);
    MessageSubscription second = subscriber(channel, b);
    first.start();
    second.start();
    assertThat(first.getSubscriptionName()).isNotEqualTo(second.getSubscriptionName());
    for (int i = 0; i < 10; i++) {
      channel.publish("change-" + i);
    }
    await()
        .untilAsserted(
            () -> {
              assertThat(a).hasSize(10);
              assertThat(b).hasSize(10);
            });
    assertThat(a).containsExactlyElementsOf(b).doesNotContain("before-subscribe");
    first.close();
    channel.publish("still-active");
    await().untilAsserted(() -> assertThat(b).hasSize(11));
    assertThat(a).hasSize(10);
    assertThat(redis.opsForStream().groups(channel.getStreamKey())).hasSize(1);
  }

  @Test
  void recoversUnacknowledgedMessagesAfterAConsumerDies() {
    RedisMessageChannel channel = pubsub.targeted("work", "workers");
    List<PubsubMessage> abandoned = new CopyOnWriteArrayList<>();
    MessageSubscription failed = channel.subscribe("failed", 10, abandoned::add, errors::add);
    subscriptions.add(failed);
    failed.start();
    for (int i = 0; i < 5; i++) {
      channel.publish("job-" + i);
    }
    await().untilAsserted(() -> assertThat(abandoned).hasSize(5));
    failed.close();
    List<String> recovered = new CopyOnWriteArrayList<>();
    MessageSubscription survivor = subscriber(channel, recovered);
    survivor.start();
    // A page with no eligible records must not prevent later pages from being inspected.
    assertThat(survivor.reclaim(Duration.ofDays(1), 2)).isZero();
    assertThat(survivor.reclaim(Duration.ZERO, 2)).isEqualTo(2);
    assertThat(recovered).containsExactly("job-2", "job-3");
    assertThat(survivor.reclaim(Duration.ZERO, 2)).isEqualTo(1);
    assertThat(survivor.reclaim(Duration.ZERO, 2)).isEqualTo(2);
    assertThat(recovered).containsExactlyInAnyOrder("job-0", "job-1", "job-2", "job-3", "job-4");
    assertThat(channel.statistics().pending()).isZero();
  }

  @Test
  void pauseAndResumeRetainsTheGroupAndItsQueuedMessages() {
    RedisMessageChannel channel = pubsub.targeted("work", "workers");
    List<String> messages = new CopyOnWriteArrayList<>();
    MessageSubscription subscription = subscriber(channel, messages);
    subscription.start();
    subscription.stop();
    channel.publish("while-paused");
    subscription.start();
    await().untilAsserted(() -> assertThat(messages).containsExactly("while-paused"));
  }

  @Test
  void legacyBindingReadsExistingAgentRecordsWithoutChangingTheStreamOrGroup() {
    redis.opsForStream().add("legacy", Map.of("agentType", "test-agent"));
    RedisMessageChannel channel = pubsub.bindTargetedStream("legacy", "runners", "legacy");
    List<PubsubMessage> messages = new CopyOnWriteArrayList<>();
    AtomicReference<MessageSubscription> reference = new AtomicReference<>();
    MessageSubscription subscription =
        channel.subscribe(
            "runner",
            1,
            message -> {
              messages.add(message);
              reference.get().ack(message);
            },
            errors::add);
    reference.set(subscription);
    subscriptions.add(subscription);
    subscription.start();
    await().untilAsserted(() -> assertThat(messages).hasSize(1));
    assertThat(messages.getFirst().attributes()).containsEntry("agentType", "test-agent");
    assertThat(messages.getFirst().payload()).isEmpty();
  }

  private MessageSubscription subscriber(RedisMessageChannel channel, List<String> messages) {
    AtomicReference<MessageSubscription> reference = new AtomicReference<>();
    MessageSubscription subscription =
        channel.subscribe(
            "replica",
            1,
            message -> {
              reference.get().ack(message);
              messages.add(message.payload());
            },
            errors::add);
    reference.set(subscription);
    subscriptions.add(subscription);
    return subscription;
  }
}
