/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor;

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.Jedis;

@Configuration
public class RedisConfig {
  @Bean(destroyMethod = "destroy")
  @Primary
  EmbeddedRedis redisServer() {
    EmbeddedRedis redis = EmbeddedRedis.embed();
    try (Jedis jedis = redis.getJedis()) {
      jedis.flushAll();
    }
    return redis;
  }

  @Bean
  @Primary
  RedisClientDelegate redisClientDelegate(EmbeddedRedis redisServer) {
    return new JedisClientDelegate(redisServer.getPool());
  }
}
