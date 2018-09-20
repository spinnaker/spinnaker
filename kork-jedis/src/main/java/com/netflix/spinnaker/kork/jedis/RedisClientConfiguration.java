/*
 * Copyright 2018 Netflix, Inc.
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

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.jedis.exception.RedisClientFactoryNotFound;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

import static com.netflix.spinnaker.kork.jedis.RedisClientConfiguration.Driver.REDIS;

/**
 * Offers a standardized Spring configuration for a named redis clients, as well as primary and previous connections.
 * This class should not be imported, but instead use JedisClientConfiguration or DynomiteClientConfiguration.
 *
 * While using this configuration, all clients are exposed through RedisClientSelector.
 *
 * This configuration also supports old-style Redis Spring configuration, as long as they wrap their Redis connection
 * pools with a RedisClientDelegate. Typically speaking, these older configuration formats should give their client
 * delegate the name "default".
 */
@Configuration
@EnableConfigurationProperties({
  RedisClientConfiguration.ClientConfigurationWrapper.class,
  RedisClientConfiguration.RedisDriverConfiguration.class,
  RedisClientConfiguration.DualClientConfiguration.class
})
public class RedisClientConfiguration {

  @Autowired
  List<RedisClientDelegateFactory> clientDelegateFactories;

  @Bean("namedRedisClients")
  public List<RedisClientDelegate> redisClientDelegates(ClientConfigurationWrapper redisClientConfigurations,
                                                        Optional<List<RedisClientDelegate>> otherRedisClientDelegates) {
    List<RedisClientDelegate> clients = new ArrayList<>();

    redisClientConfigurations.clients.forEach((name, config) -> {
      if (config.primary != null) {
        clients
          .add(getClientFactoryForDriver(config.primary.driver)
            .build(
              RedisClientSelector.getName(true, name),
              config.primary.config
            )
          );
      }
      if (config.previous != null) {
        clients
          .add(getClientFactoryForDriver(config.previous.driver)
            .build(
              RedisClientSelector.getName(false, name),
              config.previous.config
            )
          );
      }
    });
    otherRedisClientDelegates.ifPresent(clients::addAll);

    // Backwards compat with `redis.connection` and `redis.connectionPrevious`
    createDefaultClientIfNotExists(clients, ConnectionCompatibility.PRIMARY, redisClientConfigurations);
    createDefaultClientIfNotExists(clients, ConnectionCompatibility.PREVIOUS, redisClientConfigurations);

    return clients;
  }

  private void createDefaultClientIfNotExists(List<RedisClientDelegate> clients,
                                              ConnectionCompatibility connection,
                                              ClientConfigurationWrapper rootConfig) {

    String name;
    if (connection == ConnectionCompatibility.PRIMARY) {
      name = "primaryDefault";
    } else {
      name = "previousDefault";
    }

    if (clients.stream().noneMatch(c -> name.equals(c.name()))) {
      Map<String, Object> properties = new HashMap<>();

      // Pre-kork redis configuration days, Redis used alternative config structure. This wee block will map the
      // connection information from the deprecated format to the new format _if_ the old format values are present and
      // new format values are missing
      if (connection == ConnectionCompatibility.PRIMARY) {
        Optional.ofNullable(rootConfig.connection).map(v -> {
          if (!Optional.ofNullable(properties.get("connection")).isPresent()) {
            properties.put("connection", v);
          }
          return v;
        });
      } else {
        Optional.ofNullable(rootConfig.connectionPrevious).map(v -> {
          if (!Optional.ofNullable(properties.get("connection")).isPresent()) {
            properties.put("connection", v);
          }
          return v;
        });
      }
      Optional.ofNullable(rootConfig.timeoutMs).map(v -> {
        if (!Optional.ofNullable(properties.get("timeoutMs")).isPresent()) {
          properties.put("timeoutMs", v);
        }
        return v;
      });

      if (Strings.isNullOrEmpty((String) properties.get("connection"))) {
        return;
      }

      clients.add(getClientFactoryForDriver(REDIS).build(name, properties));
    }
  }

  @Bean
  public RedisClientSelector redisClientSelector(
    @Qualifier("namedRedisClients") List<RedisClientDelegate> redisClientDelegates
  ) {
    return new RedisClientSelector(redisClientDelegates);
  }

  private RedisClientDelegateFactory<?> getClientFactoryForDriver(Driver driver) {
    return clientDelegateFactories.stream()
      .filter(it -> it.supports(driver))
      .findFirst()
      .orElseThrow(() -> new RedisClientFactoryNotFound("Could not find factory for driver: " + driver.name()));
  }

  private enum ConnectionCompatibility {
    PRIMARY("connection"),
    PREVIOUS("connectionPrevious");

    private final String value;

    ConnectionCompatibility(String value) {
      this.value = value;
    }
  }

  public enum Driver {
    REDIS("redis"),
    DYNOMITE("dynomite");

    private final String value;

    Driver(String value) {
      this.value = value;
    }
  }

  @ConfigurationProperties(prefix = "redis")
  public static class ClientConfigurationWrapper {
    Map<String, DualClientConfiguration> clients = new HashMap<>();

    /**
     * Backwards compatibility of pre-kork config format
     */
    String connection;

    /**
     * Backwards compatibility of pre-kork config format
     */
    String connectionPrevious;

    /**
     * Backwards compatibility of pre-kork config format
     */
    Integer timeoutMs;

    public Map<String, DualClientConfiguration> getClients() {
      return clients;
    }

    public void setClients(Map<String, DualClientConfiguration> clients) {
      this.clients = clients;
    }

    public String getConnection() {
      return connection;
    }

    public void setConnection(String connection) {
      this.connection = connection;
    }

    public Integer getTimeoutMs() {
      return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
      this.timeoutMs = timeoutMs;
    }
  }

  @ConfigurationProperties
  public static class RedisDriverConfiguration {
    public Driver driver = REDIS;
    public Map<String, Object> config = new HashMap<>();

    public Driver getDriver() {
      return driver;
    }

    public void setDriver(Driver driver) {
      this.driver = driver;
    }

    public Map<String, Object> getConfig() {
      return config;
    }

    public void setConfig(Map<String, Object> config) {
      this.config = config;
    }
  }

  @ConfigurationProperties
  public static class DualClientConfiguration {
    public RedisDriverConfiguration primary;
    public RedisDriverConfiguration previous;

    public RedisDriverConfiguration getPrimary() {
      return primary;
    }

    public void setPrimary(RedisDriverConfiguration primary) {
      this.primary = primary;
    }

    public RedisDriverConfiguration getPrevious() {
      return previous;
    }

    public void setPrevious(RedisDriverConfiguration previous) {
      this.previous = previous;
    }
  }
}
