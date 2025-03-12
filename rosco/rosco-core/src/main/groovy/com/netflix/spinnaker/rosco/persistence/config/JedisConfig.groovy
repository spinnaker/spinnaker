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

package com.netflix.spinnaker.rosco.persistence.config

import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

@Configuration
class JedisConfig {

  @Bean
  JedisPool jedisPool(@Value('${redis.connection:redis://localhost:6379}') String connection,
                      @Value('${redis.timeout:2000}') int timeout) {
    RedisConnectionInfo connectionInfo = RedisConnectionInfo.parseConnectionUri(connection)
    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig()
    poolConfig.setMaxTotal(100)
    poolConfig.setMinIdle(25)
    poolConfig.setMaxIdle(100)
    new JedisPool(poolConfig, connectionInfo.host, connectionInfo.port, timeout, connectionInfo.password, connectionInfo.isSSL)
  }

  @Bean
  HealthIndicator redisHealth(JedisPool jedisPool) {
    final JedisPool src = jedisPool
    new HealthIndicator() {
      @Override
      Health health() {
        Jedis jedis = null
        Health.Builder health = null
        try {
          jedis = src.resource
          if ('PONG'.equals(jedis.ping())) {
            health = Health.up()
          } else {
            health = Health.down()
          }
        } catch (Exception ex) {
          health = Health.down(ex)
        } finally {
          jedis?.close()
        }
        def internal = jedisPool.internalPool //thx groovy
        health.withDetail('maxIdle', internal.maxIdle)
        health.withDetail('minIdle', internal.minIdle)
        health.withDetail('numActive', internal.numActive)
        health.withDetail('numIdle', internal.numIdle)
        health.withDetail('numWaiters', internal.numWaiters)

        return health.build()
      }
    }
  }

}
