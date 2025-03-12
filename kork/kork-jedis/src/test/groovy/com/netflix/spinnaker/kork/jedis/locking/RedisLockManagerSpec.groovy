/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.jedis.locking

import java.util.function.Consumer
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager
import com.netflix.spinnaker.kork.lock.BaseLockManagerSpec
import redis.clients.jedis.commands.JedisCommands
import redis.clients.jedis.JedisPool
import spock.lang.Shared

class RedisLockManagerSpec extends BaseLockManagerSpec<RedisLockManager> {
  @Shared def embeddedRedis = EmbeddedRedis.embed()
  def jedisPool = embeddedRedis.getPool() as JedisPool
  def objectMapper = new ObjectMapper()
  def registry = new NoopRegistry()
  def redisClientDelegate = new JedisClientDelegate(jedisPool)
  def heartbeatRateMillis = 30L

  @Override
  protected RedisLockManager subject() {
    return new RedisLockManager(
      "testOwner",
      clock,
      registry,
      objectMapper,
      redisClientDelegate,
      Optional.of(heartbeatRateMillis),
      Optional.of(testLockMaxDurationMillis)
    )
  }

  def setup() {
    jedisPool.resource.withCloseable {
      it.flushDB()
    }
  }

  def cleanupSpec() {
    embeddedRedis.destroy()
  }

  @Override protected void ensureLockExists(String lockName) {
    redisClientDelegate.withCommandsClient({ JedisCommands c ->
      // ensure that the lock was actually created
      assert c.exists("{korkLock:${lockName.toLowerCase()}}")
    } as Consumer<JedisCommands>)
  }

  @Override protected void ensureLockReleased(String lockName) {
    redisClientDelegate.withCommandsClient({ JedisCommands c ->
      // ensure that the lock no longer exists (default `successInterval` of zero should immediately expire the lock)
      assert !c.exists("{korkLock:${lockName.toLowerCase()}}")
    } as Consumer<JedisCommands>)
  }

  @Override protected void ensureLockTtlGreaterThan(String lockName, int ttlSeconds) {
    redisClientDelegate.withCommandsClient({ JedisCommands c ->
      // ensure that the lock exists and has been tt'l corresponding to the `successInterval`
      assert c.ttl("{korkLock:${lockName.toLowerCase()}}") > ttlSeconds
    } as Consumer<JedisCommands>)
  }

  @Override protected void ensureLockTtlLessThanOrEqualTo(String lockName, int ttlSeconds) {
    redisClientDelegate.withCommandsClient({ JedisCommands c ->
      // ensure that the lock exists and has been tt'l corresponding to the `failureInterval`
      assert c.ttl("{korkLock:${lockName.toLowerCase()}}") <= ttlSeconds
    } as Consumer<JedisCommands>)
  }
}
