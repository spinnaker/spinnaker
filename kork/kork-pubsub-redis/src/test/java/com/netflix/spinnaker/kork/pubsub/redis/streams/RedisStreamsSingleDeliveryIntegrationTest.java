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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises real Redis Streams consumer-group semantics (exactly-once-across-a-fleet delivery and
 * XCLAIM-based reclaim of abandoned records) against a real Valkey instance - the mocked unit tests
 * can't validate the guarantee Redis itself provides, only that this module configures/drives it
 * correctly.
 */
class RedisStreamsSingleDeliveryIntegrationTest {

  private static GenericContainer<?> valkey;
  private static StringRedisTemplate redisTemplate;

  @BeforeAll
  static void startValkey() {
    valkey =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8")).withExposedPorts(6379);
    valkey.start();

    LettuceConnectionFactory connectionFactory =
        new LettuceConnectionFactory(valkey.getHost(), valkey.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void stopValkey() {
    if (valkey != null) {
      valkey.stop();
    }
  }

  @Test
  void twoConsumersInTheSameGroupNeverProcessTheSameMessageTwice() {
    String stream = "test-stream-" + UUID.randomUUID();
    String group = "test-group";
    redisTemplate.opsForStream().createGroup(stream, ReadOffset.from("0"), group);

    for (int i = 0; i < 10; i++) {
      redisTemplate.opsForStream().add(stream, Map.of("message", "msg-" + i));
    }

    List<MapRecord<String, Object, Object>> forConsumerA =
        redisTemplate
            .opsForStream()
            .read(
                Consumer.from(group, "consumer-a"),
                org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(5),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
    List<MapRecord<String, Object, Object>> forConsumerB =
        redisTemplate
            .opsForStream()
            .read(
                Consumer.from(group, "consumer-b"),
                org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(5),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));

    assertThat(forConsumerA).isNotEmpty();
    assertThat(forConsumerB).isNotEmpty();
    assertThat(forConsumerA.size() + forConsumerB.size()).isEqualTo(10);

    List<RecordId> idsA = forConsumerA.stream().map(MapRecord::getId).toList();
    List<RecordId> idsB = forConsumerB.stream().map(MapRecord::getId).toList();
    assertThat(idsA).doesNotContainAnyElementsOf(idsB);
  }

  @Test
  void abandonedRecordsAreReclaimedViaXclaim() throws InterruptedException {
    String stream = "test-stream-" + UUID.randomUUID();
    String group = "test-group";
    redisTemplate.opsForStream().createGroup(stream, ReadOffset.from("0"), group);
    redisTemplate.opsForStream().add(stream, Map.of("message", "hello"));

    // consumer-a reads it but crashes before acking
    List<MapRecord<String, Object, Object>> delivered =
        redisTemplate
            .opsForStream()
            .read(
                Consumer.from(group, "consumer-a"),
                org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(1),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
    assertThat(delivered).hasSize(1);
    RecordId recordId = delivered.get(0).getId();

    // give it a moment so elapsed-since-delivery is measurably non-zero, then reclaim with a
    // minIdleTime of zero so the just-delivered record is immediately eligible
    Thread.sleep(50);
    PendingMessages pending =
        redisTemplate.opsForStream().pending(stream, group, Range.unbounded(), 100);
    assertThat(pending.stream().map(m -> m.getId()).toList()).contains(recordId);

    List<MapRecord<String, String, String>> claimed =
        redisTemplate
            .<String, String>opsForStream()
            .claim(stream, group, "consumer-b", Duration.ZERO, recordId);
    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).getId()).isEqualTo(recordId);

    redisTemplate.opsForStream().acknowledge(stream, group, recordId);
    PendingMessages afterAck =
        redisTemplate.opsForStream().pending(stream, group, Range.unbounded(), 100);
    assertThat(afterAck.stream().toList()).isEmpty();
  }
}
