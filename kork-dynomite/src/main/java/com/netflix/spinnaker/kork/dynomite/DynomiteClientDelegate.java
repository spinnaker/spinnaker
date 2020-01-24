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
package com.netflix.spinnaker.kork.dynomite;

import com.netflix.dyno.connectionpool.CursorBasedResult;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.dyno.jedis.DynoJedisPipeline;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisScanResult;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.commands.BinaryJedisCommands;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.commands.MultiKeyCommands;
import redis.clients.jedis.commands.RedisPipeline;
import redis.clients.jedis.commands.ScriptingCommands;
import redis.clients.jedis.exceptions.JedisException;

public class DynomiteClientDelegate implements RedisClientDelegate {

  private final String name;
  private final DynoJedisClient client;

  public DynomiteClientDelegate(DynoJedisClient client) {
    this("default", client);
  }

  public DynomiteClientDelegate(String name, DynoJedisClient client) {
    this.name = name;
    this.client = client;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public <R> R withCommandsClient(Function<JedisCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withCommandsClient(Consumer<JedisCommands> f) {
    f.accept(client);
  }

  @Override
  public <R> R withMultiClient(Function<MultiKeyCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withMultiClient(Consumer<MultiKeyCommands> f) {
    f.accept(client);
  }

  @Override
  public <R> R withBinaryClient(Function<BinaryJedisCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withBinaryClient(Consumer<BinaryJedisCommands> f) {
    f.accept(client);
  }

  @Override
  public void withPipeline(Consumer<RedisPipeline> f) {
    DynoJedisPipeline p = client.pipelined();
    try {
      f.accept(p);
    } catch (DynoException | JedisException e) {
      try {
        p.close();
      } catch (Exception ne) {
        throw new ClientDelegateException("Failed closing pipeline connection", ne);
      }
      throw new ClientDelegateException("Internal exception during pipelining", e);
    }
  }

  @Override
  public <R> R withPipeline(Function<RedisPipeline, R> f) {
    DynoJedisPipeline p = client.pipelined();
    try {
      return f.apply(p);
    } catch (DynoException | JedisException e) {
      try {
        p.close();
      } catch (Exception ne) {
        throw new ClientDelegateException("Failed closing pipeline connection", ne);
      }
      throw new ClientDelegateException("Internal exception during pipelining", e);
    }
  }

  @Override
  public void syncPipeline(RedisPipeline p) {
    if (!(p instanceof DynoJedisPipeline)) {
      throw new IllegalArgumentException(
          "Invalid RedisPipeline implementation: " + p.getClass().getName());
    }

    ((DynoJedisPipeline) p).sync();
  }

  @Override
  public boolean supportsMultiKeyPipelines() {
    return false;
  }

  @Override
  public void withMultiKeyPipeline(Consumer<Pipeline> f) {
    throw new UnsupportedOperationException(
        "Dynomite does not support multi-key pipelined operations");
  }

  @Override
  public <R> R withMultiKeyPipeline(Function<Pipeline, R> f) {
    throw new UnsupportedOperationException(
        "Dynomite does not support multi-key pipelined operations");
  }

  @Override
  public boolean supportsTransactions() {
    return false;
  }

  @Override
  public void withTransaction(Consumer<Transaction> f) {
    throw new UnsupportedOperationException("Dynomite does not support transactions");
  }

  @Override
  public <R> R withTransaction(Function<Transaction, R> f) {
    throw new UnsupportedOperationException("Dynomite does not support transactions");
  }

  @Override
  public boolean supportsScripting() {
    return true;
  }

  @Override
  public void withScriptingClient(Consumer<ScriptingCommands> f) {
    f.accept(client);
  }

  @Override
  public <R> R withScriptingClient(Function<ScriptingCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withKeyScan(String pattern, int count, Consumer<RedisScanResult> f) {
    CursorBasedResult<String> result = null;

    do {
      result = client.dyno_scan(result, count, pattern);
      final List<String> results = result.getResult();
      f.accept(() -> results);

    } while (!result.isComplete());
  }

  public class ClientDelegateException extends RuntimeException {
    public ClientDelegateException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
