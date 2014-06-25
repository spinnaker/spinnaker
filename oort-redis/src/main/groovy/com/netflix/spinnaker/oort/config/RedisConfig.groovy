/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.config

import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.redis.JedisCacheService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.embedded.RedisServer

import javax.annotation.PreDestroy

@Configuration
@DependsOn('oortAwsConfig')
class RedisConfig {

  RedisServer redisServer

  @Bean
  @Scope('singleton')
  JedisPool jedisPool(@Value('${redis.host:localhost}') String host, @Value('${redis.port:6137}') int port) {
//    if (host == 'localhost') {
//      redisServer = new RedisServer('/usr/local/bin/redis-server' as File, port)
//      redisServer.start()
//    }
    def pool = new JedisPoolConfig()
    pool.maxIdle = 100
    pool.maxTotal = 100
    new JedisPool(pool, host, port)
  }

  @PreDestroy
  public void shutdown() {
    redisServer?.stop()
  }

  @Bean
  CacheService cacheService() {
    new JedisCacheService()
  }
}
