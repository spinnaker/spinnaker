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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.commands.BinaryJedisCommands;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.commands.MultiKeyCommands;
import redis.clients.jedis.commands.RedisPipeline;
import redis.clients.jedis.commands.ScriptingCommands;
import redis.clients.jedis.util.Pool;

public class JedisClientDelegate implements RedisClientDelegate {

  private final String name;
  private final Pool<Jedis> jedisPool;

  public JedisClientDelegate(Pool<Jedis> jedisPool) {
    this("default", jedisPool);
  }

  public JedisClientDelegate(String name, Pool<Jedis> jedisPool) {
    this.name = name;
    this.jedisPool = jedisPool;
  }

  @Override
  public String name() {
    return name;
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
  public void syncPipeline(RedisPipeline p) {
    if (!(p instanceof Pipeline)) {
      throw new IllegalArgumentException(
          "Invalid RedisPipeline implementation: " + p.getClass().getName());
    }

    ((Pipeline) p).sync();
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
  public boolean supportsTransactions() {
    return true;
  }

  @Override
  public void withTransaction(Consumer<Transaction> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      f.accept(jedis.multi());
    }
  }

  @Override
  public <R> R withTransaction(Function<Transaction, R> f) {
    try (Jedis jedis = jedisPool.getResource()) {
      return f.apply(jedis.multi());
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

  @Override
  public void withKeyScan(String pattern, int count, Consumer<RedisScanResult> f) {
    ScanParams params = new ScanParams().match(pattern).count(count);
    String cursor = ScanParams.SCAN_POINTER_START;

    try (Jedis jedis = jedisPool.getResource()) {
      do {
        ScanResult<String> result = jedis.scan(cursor, params);

        final List<String> results = result.getResult();
        f.accept(() -> results);

        cursor = result.getCursor();
      } while (!"0".equals(cursor));
    }
  }
}
