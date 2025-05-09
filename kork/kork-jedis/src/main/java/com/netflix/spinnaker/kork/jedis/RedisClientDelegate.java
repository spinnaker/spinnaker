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
import redis.clients.jedis.commands.JedisBinaryCommands;
import redis.clients.jedis.commands.JedisCommands;

/**
 * Offers a functional interface over either a vanilla Jedis or Dynomite client.
 *
 * <p>TODO rz - remove withKeyScan once Dyno implements the Jedis interfaces
 */
public interface RedisClientDelegate {

  String name();

  <R> R withCommandsClient(Function<JedisCommands, R> f);

  void withCommandsClient(Consumer<JedisCommands> f);

  <R> R withMultiClient(Function<JedisCommands, R> f);

  void withMultiClient(Consumer<JedisCommands> f);

  <R> R withBinaryClient(Function<JedisBinaryCommands, R> f);

  void withBinaryClient(Consumer<JedisBinaryCommands> f);

  void withPipeline(Consumer<Pipeline> f);

  <R> R withPipeline(Function<Pipeline, R> f);

  void syncPipeline(Pipeline p);

  boolean supportsMultiKeyPipelines();

  void withMultiKeyPipeline(Consumer<Pipeline> f);

  <R> R withMultiKeyPipeline(Function<Pipeline, R> f);

  boolean supportsTransactions();

  void withTransaction(Consumer<Transaction> f);

  <R> R withTransaction(Function<Transaction, R> f);

  boolean supportsScripting();

  void withScriptingClient(Consumer<JedisCommands> f);

  <R> R withScriptingClient(Function<JedisCommands, R> f);

  void withKeyScan(String pattern, int count, Consumer<RedisScanResult> f);
}
