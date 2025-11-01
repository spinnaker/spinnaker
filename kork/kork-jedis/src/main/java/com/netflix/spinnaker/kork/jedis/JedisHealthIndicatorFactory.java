/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.jedis;

import java.lang.reflect.Field;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.Pool;

@SuppressWarnings("unchecked")
public class JedisHealthIndicatorFactory {

  public static HealthIndicator build(JedisClientDelegate client) {
    try {
      final JedisClientDelegate src = client;
      final Field clientAccess = JedisClientDelegate.class.getDeclaredField("jedisPool");
      clientAccess.setAccessible(true);

      return build((Pool<Jedis>) clientAccess.get(src));
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new BeanCreationException("Error creating Redis health indicator", e);
    }
  }

  public static HealthIndicator build(Pool<Jedis> jedisPool) {
    final Pool<Jedis> src = jedisPool;
    return () -> {
      Jedis jedis = null;
      Health.Builder health;
      try {
        jedis = src.getResource();
        if ("PONG".equals(jedis.ping())) {
          health = Health.up();
        } else {
          health = Health.down();
        }
      } catch (Exception ex) {
        health = Health.down(ex);
      } finally {
        if (jedis != null) jedis.close();
      }
      health.withDetail("maxIdle", jedisPool.getMaxIdle());
      health.withDetail("minIdle", jedisPool.getMinIdle());
      health.withDetail("numActive", jedisPool.getNumActive());
      health.withDetail("numIdle", jedisPool.getNumIdle());
      health.withDetail("numWaiters", jedisPool.getNumWaiters());

      return health.build();
    };
  }
}
