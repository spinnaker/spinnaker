/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.config.RedisReplicationLagAwareRepositoryProperties;
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration;
import de.huxhorn.sulky.ulid.ULID;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {EmbeddedRedisConfiguration.class})
public class RedisReplicationLagAwareRepositoryTest {
  @Autowired RedisClientDelegate redisClientDelegate;

  RedisReplicationLagAwareRepository redisReplicationLagAwareRepository;

  ULID ulidGenerator = new ULID();

  @BeforeEach
  void setup() {
    redisReplicationLagAwareRepository =
        new RedisReplicationLagAwareRepository(
            redisClientDelegate, new RedisReplicationLagAwareRepositoryProperties());
  }

  @Test
  void testGetAndUpdatePipelineExecution() {
    // given
    String id = ulidGenerator.nextULID();
    Instant latestUpdate = Instant.ofEpochMilli(10000);
    Instant newLatestUpdate = Instant.ofEpochMilli(20000);

    // when
    redisReplicationLagAwareRepository.putPipelineExecutionUpdate(id, latestUpdate);

    // then
    Instant actualLatestUpdate = redisReplicationLagAwareRepository.getPipelineExecutionUpdate(id);
    assertThat(latestUpdate).isEqualTo(actualLatestUpdate);

    // when
    redisReplicationLagAwareRepository.putPipelineExecutionUpdate(id, newLatestUpdate);

    // then
    actualLatestUpdate = redisReplicationLagAwareRepository.getPipelineExecutionUpdate(id);
    assertThat(newLatestUpdate).isEqualTo(actualLatestUpdate);
  }

  @Test
  void testPipelineExecutionTTLChangesAfterPut() throws InterruptedException {
    // given
    String id = ulidGenerator.nextULID();
    redisReplicationLagAwareRepository.putPipelineExecutionUpdate(id, Instant.now());
    Long currentExpiry = getPipelineExecutionExpiryMillis(id);

    // when
    Thread.sleep(50);
    redisReplicationLagAwareRepository.putPipelineExecutionUpdate(id, Instant.now());

    // then
    Long newExpiry = getPipelineExecutionExpiryMillis(id);
    assertThat(currentExpiry).isNotEqualTo(newExpiry);
  }

  @Test
  void testGetAndUpdateStageExecution() {
    // given
    String id = ulidGenerator.nextULID();
    Instant latestUpdate = Instant.ofEpochMilli(10000);
    Instant newLatestUpdate = Instant.ofEpochMilli(20000);

    // when
    redisReplicationLagAwareRepository.putStageExecutionUpdate(id, latestUpdate);
    Instant actualLatestUpdate = redisReplicationLagAwareRepository.getStageExecutionUpdate(id);

    // then
    assertThat(latestUpdate).isEqualTo(actualLatestUpdate);

    // when
    redisReplicationLagAwareRepository.putStageExecutionUpdate(id, newLatestUpdate);
    actualLatestUpdate = redisReplicationLagAwareRepository.getStageExecutionUpdate(id);
    assertThat(newLatestUpdate).isEqualTo(actualLatestUpdate);
  }

  @Test
  void testStageExecutionTTLChangesAfterPut() throws InterruptedException {
    // given
    String id = ulidGenerator.nextULID();
    redisReplicationLagAwareRepository.putStageExecutionUpdate(id, Instant.now());
    Long currentExpiry = getStageExecutionExpiryMillis(id);

    // when
    Thread.sleep(50);
    redisReplicationLagAwareRepository.putStageExecutionUpdate(id, Instant.now());

    // then
    Long newExpiry = getStageExecutionExpiryMillis(id);
    assertThat(currentExpiry).isNotEqualTo(newExpiry);
  }

  @Test
  void testGetUnknownPipelineExecutionId() {
    Instant actualLatestUpdate =
        redisReplicationLagAwareRepository.getPipelineExecutionUpdate("doesNotExist");
    assertThat(actualLatestUpdate).isNull();
  }

  @Test
  void testGetUnknownStageExecutionId() {
    Instant actualLatestUpdate =
        redisReplicationLagAwareRepository.getStageExecutionUpdate("doesNotExist");
    assertThat(actualLatestUpdate).isNull();
  }

  private Long getPipelineExecutionExpiryMillis(String id) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          Long ttl = c.pttl(redisReplicationLagAwareRepository.getPipelineExecutionKey(id));
          return Instant.now().toEpochMilli() + ttl;
        });
  }

  private Long getStageExecutionExpiryMillis(String id) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          Long ttl = c.pttl(redisReplicationLagAwareRepository.getStageExecutionKey(id));
          return Instant.now().toEpochMilli() + ttl;
        });
  }
}
