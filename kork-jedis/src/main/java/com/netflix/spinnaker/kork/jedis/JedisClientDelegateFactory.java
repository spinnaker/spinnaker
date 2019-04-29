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

import static com.netflix.spinnaker.kork.jedis.RedisClientConfiguration.Driver.REDIS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.RedisClientConfiguration.Driver;
import java.util.Map;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class JedisClientDelegateFactory implements RedisClientDelegateFactory<JedisClientDelegate> {

  private Registry registry;
  private ObjectMapper objectMapper;
  private GenericObjectPoolConfig objectPoolConfig;

  public JedisClientDelegateFactory(
      Registry registry, ObjectMapper objectMapper, GenericObjectPoolConfig objectPoolConfig) {
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.objectPoolConfig = objectPoolConfig;
  }

  @Override
  public boolean supports(Driver driver) {
    return driver == REDIS;
  }

  @Override
  public JedisClientDelegate build(String name, Map<String, Object> properties) {
    JedisDriverProperties props =
        objectMapper.convertValue(properties, JedisDriverProperties.class);
    return new JedisClientDelegate(
        name, new JedisPoolFactory(registry).build(name, props, objectPoolConfig));
  }
}
