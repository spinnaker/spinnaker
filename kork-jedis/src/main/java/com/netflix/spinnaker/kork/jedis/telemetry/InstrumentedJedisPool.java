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
import java.lang.reflect.Field;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class InstrumentedJedisPool extends JedisPool {

  private final Registry registry;
  private final JedisPool delegated;
  private final String poolName;

  private GenericObjectPool<Jedis> delegateInternalPool;

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
    if (delegateInternalPool == null) {
      try {
        Field f = delegated.getClass().getDeclaredField("internalPool");
        f.setAccessible(true);
        delegateInternalPool = (GenericObjectPool<Jedis>) f.get(delegated);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new IllegalStateException("Could not get reference to delegate's internal pool", e);
      }
    }
    return delegateInternalPool;
  }

  @Override
  public Jedis getResource() {
    return new InstrumentedJedis(registry, delegated.getResource(), poolName);
  }

  @Override
  public void returnResourceObject(Jedis resource) {
    super.returnResourceObject(unwrapResource(resource));
  }

  @Override
  protected void returnBrokenResourceObject(Jedis resource) {
    super.returnBrokenResourceObject(unwrapResource(resource));
  }

  @Override
  public void close() {
    delegated.close();
  }

  @Override
  public boolean isClosed() {
    return delegated.isClosed();
  }

  @Override
  public void initPool(GenericObjectPoolConfig poolConfig, PooledObjectFactory<Jedis> factory) {
    // Explicitly not initializing the pool here, as the delegated pool will initialize itself
  }

  @Override
  public void destroy() {
    delegated.destroy();
  }

  @Override
  protected void closeInternalPool() {
    // Explicitly not calling this; destroy and initPool are the only references to this method
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
  public long getMeanBorrowWaitTimeMillis() {
    return getInternalPoolReference().getMeanBorrowWaitTimeMillis();
  }

  @Override
  public long getMaxBorrowWaitTimeMillis() {
    return getInternalPoolReference().getMaxBorrowWaitTimeMillis();
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
