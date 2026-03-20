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

package com.netflix.spinnaker.clouddriver.core;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.jedis.RedisTaskRepository;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.telemetry.InstrumentedJedisPool;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

@Configuration
@ConditionalOnProperty(value = "redis.enabled", matchIfMissing = true)
@EnableConfigurationProperties(RedisConfigurationProperties.class)
public class RedisConfig {

  @Bean
  @ConfigurationProperties("redis")
  GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setMaxTotal(100);
    config.setMaxIdle(100);
    config.setMinIdle(25);
    return config;
  }

  @Bean
  @ConditionalOnExpression("${redis.task-repository.enabled:true}")
  TaskRepository taskRepository(
      RedisClientDelegate redisClientDelegate,
      Optional<RedisClientDelegate> redisClientDelegatePrevious) {
    return new RedisTaskRepository(redisClientDelegate, redisClientDelegatePrevious);
  }

  @Bean
  RedisClientDelegate redisClientDelegate(JedisPool jedisPool) {
    return new JedisClientDelegate(jedisPool);
  }

  @Bean
  @ConditionalOnBean(value = JedisPool.class, name = "jedisPoolPrevious")
  RedisClientDelegate redisClientDelegatePrevious(JedisPool jedisPoolPrevious) {
    return new JedisClientDelegate(jedisPoolPrevious);
  }

  @Bean
  JedisPool jedisPool(
      Registry registry,
      RedisConfigurationProperties redisConfigurationProperties,
      GenericObjectPoolConfig redisPoolConfig) {
    JedisPool jedisPool =
        createPool(
            registry,
            redisPoolConfig,
            redisConfigurationProperties.getConnection(),
            redisConfigurationProperties.getTimeout(),
            "primaryDefault");

    if (jedisPool instanceof InstrumentedJedisPool) {
      GenericObjectPool internalPool =
          ((InstrumentedJedisPool) jedisPool).getInternalPoolReference();

      registry.gauge("jedis.pool.maxIdle", internalPool, p -> (double) p.getMaxIdle());
      registry.gauge("jedis.pool.minIdle", internalPool, p -> (double) p.getMinIdle());
      registry.gauge("jedis.pool.numActive", internalPool, p -> (double) p.getNumActive());
      registry.gauge("jedis.pool.numIdle", internalPool, p -> (double) p.getNumIdle());
      registry.gauge("jedis.pool.numWaiters", internalPool, p -> (double) p.getNumWaiters());
    }

    return jedisPool;
  }

  private static JedisPool createPool(
      Registry registry,
      GenericObjectPoolConfig redisPoolConfig,
      String connection,
      int timeout,
      String name) {
    URI redisConnection = URI.create(connection);

    String host = redisConnection.getHost();
    int port = redisConnection.getPort() == -1 ? Protocol.DEFAULT_PORT : redisConnection.getPort();

    String path = redisConnection.getPath();
    int database =
        Integer.parseInt(
            (path != null && !path.isEmpty() ? path : "/" + Protocol.DEFAULT_DATABASE)
                .split("/", 2)[1]);

    String userInfo = redisConnection.getUserInfo();
    String password = userInfo != null ? userInfo.split(":", 2)[1] : null;

    boolean isSSL = "rediss".equals(redisConnection.getScheme());

    return new InstrumentedJedisPool(
        registry,
        new JedisPool(
            redisPoolConfig != null ? redisPoolConfig : new GenericObjectPoolConfig(),
            host,
            port,
            timeout,
            password,
            database,
            isSSL),
        name);
  }

  @Bean
  JedisPool jedisPoolPrevious(
      Registry registry, RedisConfigurationProperties redisConfigurationProperties) {
    if (redisConfigurationProperties.getConnectionPrevious() == null
        || redisConfigurationProperties
            .getConnection()
            .equals(redisConfigurationProperties.getConnectionPrevious())) {
      return null;
    }
    return createPool(
        registry,
        null,
        redisConfigurationProperties.getConnectionPrevious(),
        1000,
        "previousDefault");
  }

  @Bean
  HealthIndicator redisHealth(JedisPool jedisPool) {
    return () -> {
      Jedis jedis = null;
      Health.Builder health;
      try {
        jedis = jedisPool.getResource();
        if ("PONG".equals(jedis.ping())) {
          health = Health.up();
        } else {
          health = Health.down();
        }
      } catch (Exception ex) {
        health = Health.down(ex);
      } finally {
        if (jedis != null) {
          jedis.close();
        }
      }

      if (jedisPool instanceof InstrumentedJedisPool) {
        GenericObjectPool internalPool =
            ((InstrumentedJedisPool) jedisPool).getInternalPoolReference();
        health.withDetail("maxIdle", internalPool.getMaxIdle());
        health.withDetail("minIdle", internalPool.getMinIdle());
        health.withDetail("numActive", internalPool.getNumActive());
        health.withDetail("numIdle", internalPool.getNumIdle());
        health.withDetail("numWaiters", internalPool.getNumWaiters());
      }

      return health.build();
    };
  }
}
