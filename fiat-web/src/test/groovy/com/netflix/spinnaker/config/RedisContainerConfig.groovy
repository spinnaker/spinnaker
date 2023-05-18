/*
 * Copyright 2016 Google, Inc.
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


package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.util.Pool

@Configuration
class RedisContainerConfig {

  @Bean(destroyMethod = "stop")
  GenericContainer redisContainer() {
    def redisContainer = new GenericContainer(DockerImageName.parse("library/redis:5-alpine"))
      .withExposedPorts(6379);

    redisContainer.start()

    return redisContainer
  }

  @Bean
  Pool<Jedis> jedisPool(GenericContainer redisContainer) {
    return new JedisPool(redisContainer.host, redisContainer.getMappedPort(6379))
  }

  @Bean
  RedisClientDelegate redisClientDelegate(Pool<Jedis> jedisPool) {
    return new JedisClientDelegate(jedisPool)
  }
}
