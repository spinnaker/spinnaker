/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.data.task.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.core.test.TaskRepositoryTck;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisTaskRepositoryTest extends TaskRepositoryTck<RedisTaskRepository> {

  JedisPool jedisPool;

  EmbeddedRedis embeddedRedis;

  @Override
  protected RedisTaskRepository createTaskRepository() {
    embeddedRedis = EmbeddedRedis.embed();
    jedisPool = (JedisPool) embeddedRedis.getPool();

    return new RedisTaskRepository(new JedisClientDelegate(jedisPool), Optional.empty());
  }

  @Test
  public void clientRequestIndexExpiresWithTheTask() {
    subject.create("TEST", "Test Status", "request-1");

    try (Jedis jedis = jedisPool.getResource()) {
      assertThat(jedis.ttl("kato:taskmap:request-1"))
          .isPositive()
          .isLessThanOrEqualTo(TimeUnit.HOURS.toSeconds(12));
    }
  }

  @Test
  public void createReplacesAnIndexEntryWhoseTaskHasExpired() {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.set("kato:taskmap:request-2", "expired-task");
    }

    Task task = subject.create("TEST", "Test Status", "request-2");

    assertThat(task).isNotNull();
    assertThat(task.getId()).isNotEqualTo("expired-task");
    assertThat(subject.getByClientRequestId("request-2").getId()).isEqualTo(task.getId());
  }

  @Test
  public void listSkipsAndRemovesRunningTasksWhoseDataHasExpired() {
    Task task = subject.create("TEST", "Test Status");
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.sadd("kato:tasks", "expired-task");
    }

    assertThat(subject.list()).extracting(Task::getId).containsExactly(task.getId());
    assertThat(subject.listByThisInstance()).extracting(Task::getId).containsExactly(task.getId());
    try (Jedis jedis = jedisPool.getResource()) {
      assertThat(jedis.smembers("kato:tasks")).containsExactly(task.getId());
    }
  }

  @AfterEach
  public void tearDown() {
    Optional.ofNullable(embeddedRedis).ifPresent(EmbeddedRedis::destroy);
  }
}
