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

  private Jedis unwrapResource(Jedis jedis) {
    if (jedis instanceof InstrumentedJedis) {
      return ((InstrumentedJedis) jedis).unwrap();
    }
    return jedis;
  }
}
