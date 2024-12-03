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
package com.netflix.spinnaker.kork.jedis.telemetry;

import com.netflix.spectator.api.Registry;
import org.apache.commons.pool2.impl.GenericObjectPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class InstrumentedJedisPool extends JedisPool {

  private final Registry registry;
  private final JedisPool delegated;
  private final String poolName;

  public InstrumentedJedisPool(Registry registry, JedisPool delegated) {
    this(registry, delegated, "unnamed");
  }

  public InstrumentedJedisPool(Registry registry, JedisPool delegated, String poolName) {
    this.registry = registry;
    this.delegated = delegated;
    this.poolName = poolName;
  }

  @SuppressWarnings("unchecked")
  public GenericObjectPool<Jedis> getInternalPoolReference() {
    if (delegated == null) {
      throw new IllegalStateException("Could not get reference to delegate's internal pool");
    }
    return delegated;
  }

  @Override
  public Jedis getResource() {
    return new InstrumentedJedis(registry, delegated.getResource(), poolName).unwrap();
  }

  @Override
  public void returnResource(Jedis resource) {
    super.returnResource(unwrapResource(resource));
  }

  @Override
  public void returnBrokenResource(Jedis resource) {
    super.returnBrokenResource(unwrapResource(resource));
  }

  @Override
  public void close() {
    delegated.close();
  }

  @Override
  public void destroy() {
    delegated.destroy();
  }

  @Override
  public int getNumActive() {
    return getInternalPoolReference().getNumActive();
  }

  @Override
  public int getNumIdle() {
    return getInternalPoolReference().getNumIdle();
  }

  @Override
  public int getNumWaiters() {
    return getInternalPoolReference().getNumWaiters();
  }

  @Override
  public void addObjects(int count) {
    delegated.addObjects(count);
  }

  private Jedis unwrapResource(Jedis jedis) {
    if (jedis instanceof InstrumentedJedis) {
      return ((InstrumentedJedis) jedis).unwrap();
    }
    return jedis;
  }
}
