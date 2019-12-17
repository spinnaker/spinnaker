/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.telemetry;

import com.netflix.spectator.api.Registry;
import java.lang.reflect.Field;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;

@Component
public class RedisPoolMetricsPostProcessor extends AbstractMetricsPostProcessor<JedisPool> {

  @Autowired
  public RedisPoolMetricsPostProcessor(Registry registry) {
    super(JedisPool.class, registry);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void applyMetrics(JedisPool bean, String beanName)
      throws NoSuchFieldException, IllegalAccessException {
    final Field poolAccess = Pool.class.getDeclaredField("internalPool");
    poolAccess.setAccessible(true);
    GenericObjectPool<Jedis> pool = (GenericObjectPool<Jedis>) poolAccess.get(bean);
    registry.gauge(
        registry.createId("redis.connectionPool.maxIdle", "poolName", beanName),
        pool,
        p -> Integer.valueOf(p.getMaxIdle()).doubleValue());
    registry.gauge(
        registry.createId("redis.connectionPool.minIdle", "poolName", beanName),
        pool,
        p -> Integer.valueOf(p.getMinIdle()).doubleValue());
    registry.gauge(
        registry.createId("redis.connectionPool.numActive", "poolName", beanName),
        bean,
        p -> Integer.valueOf(p.getNumActive()).doubleValue());
    registry.gauge(
        registry.createId("redis.connectionPool.numIdle", "poolName", beanName),
        bean,
        p -> Integer.valueOf(p.getNumIdle()).doubleValue());
    registry.gauge(
        registry.createId("redis.connectionPool.numWaiters", "poolName", beanName),
        bean,
        p -> Integer.valueOf(p.getNumWaiters()).doubleValue());
  }
}
