package com.netflix.spinnaker.kork.jedis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;
import redis.embedded.RedisServer;

public class EmbeddedRedis implements AutoCloseable {

  private final URI connection;
  private final RedisServer redisServer;

  private Pool<Jedis> jedis;

  private EmbeddedRedis(int port) throws IOException, URISyntaxException {
    this.connection = URI.create(String.format("redis://127.0.0.1:%d/0", port));
    this.redisServer =
        RedisServer.builder()
            .port(port)
            .setting("bind 127.0.0.1")
            .setting("appendonly no")
            .setting("save \"\"")
            .setting("databases 1")
            .build();
    this.redisServer.start();
  }

  @Override
  public void close() {
    destroy();
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
