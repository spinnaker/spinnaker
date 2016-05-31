/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.redis

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool
import redis.embedded.RedisServer

public class EmbeddedRedis {

  private final URI connection;
  private final RedisServer redisServer;

  private Pool<Jedis> jedis;

  private EmbeddedRedis(int port) throws IOException, URISyntaxException {
    this.connection = URI.create(String.format("redis://127.0.0.1:%d/0", port));
    this.redisServer = RedisServer
        .builder()
        .port(port)
        .setting("bind 127.0.0.1")
        .setting("appendonly no")
        .setting("save \"\"")
        .setting("databases 1")
        .build();
    this.redisServer.start();
  }

  public void destroy() {
    try {
      this.redisServer.stop();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public int getPort() {
    return redisServer.ports().get(0);
  }

  public Pool<Jedis> getPool() {
    if (jedis == null) {
      jedis = new JedisPool(connection);
    }
    return jedis;
  }

  public Jedis getJedis() {
    return getPool().getResource();
  }

  public static EmbeddedRedis embed() {
    try {
      ServerSocket serverSocket = new ServerSocket(0);
      int port = serverSocket.getLocalPort();
      serverSocket.close();
      return new EmbeddedRedis(port);
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException("Failed to create embedded Redis", e);
    }
  }
}