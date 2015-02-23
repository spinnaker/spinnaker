/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.test.redis

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import groovy.transform.CompileDynamic
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool

/**
 *
 * @author sthadeshwar
 */
class EmbeddedRedisConfiguration {

  @Bean
  EmbeddedRedis redisServer() {
    println "redis.connection = " + System.getProperty("redis.connection")
    EmbeddedRedis.embed()
  }

  @Bean
  @ConditionalOnBean(EmbeddedRedis)
  @CompileDynamic
  Config embeddedJesqueConfig(EmbeddedRedis redis) {
    new ConfigBuilder()
        .withHost("localhost")
        .withPort(redis.redisServer.port)
        .build()
  }

  @Bean
  @ConditionalOnBean(EmbeddedRedis)
  @CompileDynamic
  Pool<Jedis> embeddedJedisPool(EmbeddedRedis redis) {
    new JedisPool("localhost", redis.redisServer.port)
  }

}
