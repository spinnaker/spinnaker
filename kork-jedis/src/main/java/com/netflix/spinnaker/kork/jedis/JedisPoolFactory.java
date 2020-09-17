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

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.exception.MissingRequiredConfiguration;
import com.netflix.spinnaker.kork.jedis.telemetry.InstrumentedJedisPool;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;

public class JedisPoolFactory {

  private final Registry registry;

  public JedisPoolFactory() {
    this(new NoopRegistry());
  }

  public JedisPoolFactory(Registry registry) {
    this.registry = registry;
  }

  public Pool<Jedis> build(
      String name, JedisDriverProperties properties, GenericObjectPoolConfig objectPoolConfig) {
    if (properties.connection == null || "".equals(properties.connection)) {
      throw new MissingRequiredConfiguration("Jedis client must have a connection defined");
    }

    RedisClientConnectionProperties cxp =
        new RedisClientConnectionProperties(URI.create(properties.connection));
    GenericObjectPoolConfig poolConfig =
        Optional.ofNullable(properties.poolConfig).orElse(objectPoolConfig);

    return new InstrumentedJedisPool(
        registry,
        // Pool name should always be "null", as setting this is incompat with some SaaS Redis
        // offerings
        new JedisPool(
            poolConfig,
            cxp.addr(),
            cxp.port(),
            properties.timeoutMs,
            cxp.password(),
            cxp.database(),
            null,
            cxp.isSSL()),
        name);
  }
}
