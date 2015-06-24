package com.netflix.spinnaker.kork.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;

public class EmbeddedRedis {

  private final String host;
  private final int port;
  private final String connection;
  private final RedisServer redisServer;

  private Jedis jedis;
  private JedisCommands jedisCommands;

  private EmbeddedRedis(String host, int port) throws IOException, URISyntaxException {
    this.host = host;
    this.port = port;
    this.connection = String.format("redis://%s:%d", host, port);
    this.redisServer = new RedisServer(port);
    this.redisServer.start();
  }

  public void destroy() {
    try {
      this.redisServer.stop();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Jedis getJedis() {
    if (jedis == null) {
      jedis = new Jedis(host, port);
    }
    return jedis;
  }

  public JedisCommands getJedisCommands() {
    if (jedisCommands == null) {
      jedisCommands = new JedisConfig().jedis(connection, 2000);
    }
    return jedisCommands;
  }

  public static EmbeddedRedis embed() {
    try {
      ServerSocket serverSocket = new ServerSocket(0);
      String host = serverSocket.getInetAddress().getCanonicalHostName();
      int port = serverSocket.getLocalPort();
      serverSocket.close();
      return new EmbeddedRedis(host, port);
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException("Failed to create embedded Redis", e);
    }
  }
}
