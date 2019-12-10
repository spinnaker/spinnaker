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

import java.util.function.Consumer;
import java.util.function.Function;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.commands.BinaryJedisCommands;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.commands.MultiKeyCommands;
import redis.clients.jedis.commands.RedisPipeline;
import redis.clients.jedis.commands.ScriptingCommands;

/**
 * Offers a functional interface over either a vanilla Jedis or Dynomite client.
 *
 * <p>TODO rz - remove withKeyScan once Dyno implements the Jedis interfaces
 */
public interface RedisClientDelegate {

  String name();

  <R> R withCommandsClient(Function<JedisCommands, R> f);

  void withCommandsClient(Consumer<JedisCommands> f);

  <R> R withMultiClient(Function<MultiKeyCommands, R> f);

  void withMultiClient(Consumer<MultiKeyCommands> f);

  <R> R withBinaryClient(Function<BinaryJedisCommands, R> f);

  void withBinaryClient(Consumer<BinaryJedisCommands> f);

  void withPipeline(Consumer<RedisPipeline> f);

  <R> R withPipeline(Function<RedisPipeline, R> f);

  void syncPipeline(RedisPipeline p);

  boolean supportsMultiKeyPipelines();

  void withMultiKeyPipeline(Consumer<Pipeline> f);

  <R> R withMultiKeyPipeline(Function<Pipeline, R> f);

  boolean supportsTransactions();

  void withTransaction(Consumer<Transaction> f);

  <R> R withTransaction(Function<Transaction, R> f);

  boolean supportsScripting();

  void withScriptingClient(Consumer<ScriptingCommands> f);

  <R> R withScriptingClient(Function<ScriptingCommands, R> f);

  void withKeyScan(String pattern, int count, Consumer<RedisScanResult> f);
}
