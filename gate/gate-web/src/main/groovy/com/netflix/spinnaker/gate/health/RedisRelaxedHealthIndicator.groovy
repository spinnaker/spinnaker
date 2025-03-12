/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.gate.health

import com.netflix.spectator.api.Registry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.util.Pool

import java.util.concurrent.atomic.AtomicReference
import java.util.function.ToDoubleFunction

/**
 * A related Redis health indicator that will:
 * - report DOWN until redis has been successfully pinged at least once
 * - report UNKNOWN if redis is unreachable but has been UP previously
 * - report UP if redis is reachable
 *
 * This indicator differs from the default in that it will _not_ report DOWN
 * if the underlying redis has been UP at least once in the past.
 *
 * It will publish a metric `redis.client.isUnhealthy` that should be alerted on.
 */
@Component
@ConditionalOnProperty("redis.use-relaxed-health-indicator")
class RedisRelaxedHealthIndicator extends AbstractHealthIndicator {
  AtomicReference<Exception> lastException = new AtomicReference<>(null)
  AtomicReference<String> redisVersion = new AtomicReference<>(null)

  private final Pool<Jedis> jedisPool

  @Autowired
  RedisRelaxedHealthIndicator(JedisPool jedisPool, Registry registry) {
    Assert.notNull(jedisPool, "JedisPool must not be null")
    Assert.notNull(registry, "Registry must not be null")

    this.jedisPool = jedisPool

    registry.gauge("redis.client.isUnhealthy", lastException, new ToDoubleFunction<AtomicReference<Exception>>() {
      @Override
      double applyAsDouble(AtomicReference<Exception> ref) {
        return ref.get() ? 1 : 0
      }
    })
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    if (redisVersion.get() && !lastException.get()) {
      builder.up().withDetail("version", redisVersion.get())
      return
    }

    if (!redisVersion.get()) {
      // report DOWN until redis has been successfully pinged
      builder.down()
      return
    }

    def exception = lastException.get()
    builder.unknown().withDetail("errors", exception.message).withDetail("version", redisVersion.get())
  }

  @Scheduled(fixedDelay = 30000L)
  void checkRedisHealth() {
    Jedis jedis = null
    try {
      jedis = jedisPool.getResource()

      def info = jedis.info("server").split("\r\n") as List<String>
      def version = info.find { it.startsWith("redis_version:") }
      if (version) {
        redisVersion.set(version.split(":")[1])
      }
      lastException.set(null)
    } catch (e) {
      lastException.set(e)
    } finally {
      jedis?.close()
    }
  }
}
