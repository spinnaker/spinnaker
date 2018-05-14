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
package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import com.netflix.spinnaker.orca.pipeline.persistence.PollingAgentExecutionRepositoryTck
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.AutoCleanup
import spock.lang.Shared

class RedisPollingAgentExecutionRepositorySpec extends PollingAgentExecutionRepositoryTck<RedisExecutionRepository> {

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedisPrevious

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    embeddedRedisPrevious = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
    embeddedRedisPrevious.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool
  Pool<Jedis> jedisPoolPrevious = embeddedRedisPrevious.pool

  RedisClientDelegate redisClientDelegate = new JedisClientDelegate("primaryDefault", jedisPool)
  RedisClientDelegate previousRedisClientDelegate = new JedisClientDelegate("previousDefault", jedisPoolPrevious)
  RedisClientSelector redisClientSelector = new RedisClientSelector([redisClientDelegate, previousRedisClientDelegate])

  Registry registry = new NoopRegistry()

  @AutoCleanup
  def jedis = jedisPool.resource

  @Override
  RedisExecutionRepository createExecutionRepository() {
    return new RedisExecutionRepository(registry, redisClientSelector, 1, 50)
  }

  @Override
  RedisExecutionRepository createExecutionRepositoryPrevious() {
    return new RedisExecutionRepository(registry, new RedisClientSelector([new JedisClientDelegate("primaryDefault", jedisPoolPrevious)]), 1, 50)
  }
}
