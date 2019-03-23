/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.redis.spring

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

@Configuration
class EmbeddedRedisConfiguration {
  @Bean(destroyMethod = "destroy")
  fun redisServer(): EmbeddedRedis =
    EmbeddedRedis.embed().apply {
      jedis.use { jedis -> jedis.flushAll() }
    }

  @Bean
  fun redisClientDelegate(redisServer: EmbeddedRedis): RedisClientDelegate {
    log.info("[redisPool] Using embedded Redis server on port {}", redisServer.port)
    return JedisClientDelegate("primaryDefault", redisServer.pool)
  }

  @Bean
  fun queueRedisPool(
    redisServer: EmbeddedRedis
  ): Pool<Jedis> {
    log.info("[queueRedisPool] using embedded Redis server on port {}", redisServer.port)
    return redisServer.pool
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
