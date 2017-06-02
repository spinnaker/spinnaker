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

package com.netflix.kayenta.persistence.config;

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import lombok.extern.slf4j.Slf4j;
import net.greghaines.jesque.Config;
import net.greghaines.jesque.ConfigBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

@Configuration
@ConditionalOnProperty(value = "kayenta.redis.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class EmbeddedRedisConfiguration {

  @Bean(destroyMethod = "destroy")
  EmbeddedRedis redisServer() {
    EmbeddedRedis redis = EmbeddedRedis.embed();
    try (Jedis jedis = redis.getJedis()) {
      jedis.flushAll();
    }
    return redis;
  }

  @Bean
  Config jesqueConfig() {
    return new ConfigBuilder().withHost("127.0.0.1")
                              .withPort(redisServer().getPort())
                              .build();
  }

  @Bean
  Pool<Jedis> jedisPool() {
    log.warn("Running with embedded redis for orchestration engine persistence layer. This configuration is only " +
             "suitable for single-node deployments.");
    return redisServer().getPool();
  }
}
