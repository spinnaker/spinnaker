/*
 * Copyright 2017 Netflix, Inc.
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

import redis.clients.jedis.BinaryJedisCommands;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.MultiKeyCommands;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.RedisPipeline;
import redis.clients.jedis.ScriptingCommands;

import java.util.function.Consumer;
import java.util.function.Function;

public class JedisClientDelegate implements RedisClientDelegate {

  private final JedisPool jedisPool;

  public JedisClientDelegate(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public <R> R withCommandsClient(Function<JedisCommands, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis);
    }
  }

  @Override
  public void withCommandsClient(Consumer<JedisCommands> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis);
    }
  }

  @Override
  public <R> R withMultiClient(Function<MultiKeyCommands, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis);
    }
  }

  @Override
  public void withMultiClient(Consumer<MultiKeyCommands> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis);
    }
  }

  @Override
  public <R> R withBinaryClient(Function<BinaryJedisCommands, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis);
    }
  }

  @Override
  public void withBinaryClient(Consumer<BinaryJedisCommands> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis);
    }
  }

  @Override
  public void withPipeline(Consumer<RedisPipeline> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis.pipelined());
    }
  }

  @Override
  public <R> R withPipeline(Function<RedisPipeline, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis.pipelined());
    }
  }

  @Override
  public boolean supportsMultiKeyPipelines() {
    return true;
  }

  @Override
  public void withMultiKeyPipeline(Consumer<Pipeline> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis.pipelined());
    }
  }

  @Override
  public <R> R withMultiKeyPipeline(Function<Pipeline, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis.pipelined());
    }
  }

  @Override
  public boolean supportsScripting() {
    return true;
  }

  @Override
  public void withScriptingClient(Consumer<ScriptingCommands> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis);
    }
  }

  @Override
  public <R> R withScriptingClient(Function<ScriptingCommands, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis);
    }
  }
}
