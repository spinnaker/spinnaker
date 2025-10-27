package com.netflix.spinnaker.fiat.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.telemetry.InstrumentedJedisPool;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import redis.clients.jedis.*;

@Slf4j
@Configuration
@ConditionalOnProperty("redis.connection")
public class RedisConfig {

  @Getter @Setter private static String clientName = defaultClientName();

  private static String defaultClientName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  @Bean
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(20);
    config.setMaxIdle(20);
    config.setMinIdle(5);
    return config;
  }

  @Bean
  public JedisPool jedisPool(
      @Value("${redis.connection:redis://localhost:6379}") String connection,
      @Value("${redis.timeout:2000}") int timeout,
      GenericObjectPoolConfig redisPoolConfig,
      Registry registry) {
    return createPool(redisPoolConfig, connection, timeout, registry);
  }

  @Bean
  RedisClientDelegate redisClientDelegate(JedisPool jedisPool) {
    return new JedisClientDelegate(jedisPool);
  }

  private static JedisPool createPool(
      GenericObjectPoolConfig redisPoolConfig, String connection, int timeout, Registry registry) {
    URI redisConnection = URI.create(connection);

    String host = redisConnection.getHost();
    int port = redisConnection.getPort() == -1 ? Protocol.DEFAULT_PORT : redisConnection.getPort();

    String path = redisConnection.getPath();
    if (StringUtils.isEmpty(path)) {
      path = "/" + Protocol.DEFAULT_DATABASE;
    }
    int database = Integer.parseInt(path.split("/", 2)[1]);

    String password = null;
    if (redisConnection.getUserInfo() != null) {
      password = redisConnection.getUserInfo().split(":", 2)[1];
    }

    boolean isSSL = redisConnection.getScheme().equals("rediss");

    if (redisPoolConfig == null) {
      redisPoolConfig = new GenericObjectPoolConfig();
    }

    return new InstrumentedJedisPool(
        registry,
        new JedisPool(redisPoolConfig, host, port, timeout, password, database, clientName, isSSL),
        "fiat");
  }
}
