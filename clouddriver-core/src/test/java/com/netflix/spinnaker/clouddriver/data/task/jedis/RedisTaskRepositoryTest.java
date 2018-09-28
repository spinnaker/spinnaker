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

import com.netflix.spinnaker.clouddriver.core.test.TaskRepositoryTck;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import org.junit.After;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

public class RedisTaskRepositoryTest extends TaskRepositoryTck<RedisTaskRepository> {

  JedisPool jedisPool;

  EmbeddedRedis embeddedRedis;

  @Override
  protected RedisTaskRepository createTaskRepository() {
    embeddedRedis = EmbeddedRedis.embed();
    jedisPool = (JedisPool) embeddedRedis.getPool();

    return new RedisTaskRepository(new JedisClientDelegate(jedisPool), Optional.empty());
  }

  @After
  public void tearDown() {
    Optional.ofNullable(embeddedRedis).ifPresent(EmbeddedRedis::destroy);
  }
}
