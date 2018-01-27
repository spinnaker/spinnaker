package com.netflix.spinnaker.orca.config;

import java.net.URI;
import redis.clients.jedis.Protocol;
import redis.clients.util.JedisURIHelper;

public class RedisConnectionInfo {
  public boolean hasPassword() {
    return password.length() > 0;
  }

  public static RedisConnectionInfo parseConnectionUri(String connection) {

    URI redisConnection = URI.create(connection);

    String host = redisConnection.getHost();
    int port = redisConnection.getPort() == -1 ? Protocol.DEFAULT_PORT : redisConnection.getPort();

    int database = JedisURIHelper.getDBIndex(redisConnection);

    String password = JedisURIHelper.getPassword(redisConnection);

    return new RedisConnectionInfo();
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getDatabase() {
    return database;
  }

  public void setDatabase(int database) {
    this.database = database;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  private String host;
  private int port;
  private int database;
  private String password;
}
