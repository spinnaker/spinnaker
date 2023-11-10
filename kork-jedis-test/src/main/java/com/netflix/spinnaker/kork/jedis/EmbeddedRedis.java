package com.netflix.spinnaker.kork.jedis;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class EmbeddedRedis implements AutoCloseable {

  private static final int REDIS_PORT = 6379;

  private final GenericContainer<?> redisContainer;

  private JedisPool jedis;

  private EmbeddedRedis() {
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("library/redis:5-alpine"))
            .withExposedPorts(REDIS_PORT);
    redisContainer.start();
  }

  @Override
  public void close() {
    destroy();
  }

  public void destroy() {
    redisContainer.stop();
  }

  public String getHost() {
    return redisContainer.getHost();
  }

  public int getPort() {
    return redisContainer.getMappedPort(REDIS_PORT);
  }

  public JedisPool getPool() {
    if (jedis == null) {
      jedis = new JedisPool(getHost(), getPort());
    }
    return jedis;
  }

  public Jedis getJedis() {
    return getPool().getResource();
  }

  public static EmbeddedRedis embed() {
    return new EmbeddedRedis();
  }
}
