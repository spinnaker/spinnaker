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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(RedisClientConfiguration.class)
public class JedisClientConfiguration {

  /**
   * Backwards compatibility with pre-kork redis config.
   *
   * <p>Services can override this pool config to provide their own default pool config. Individual
   * clients can also override their own pool config.
   */
  @Bean
  @ConditionalOnMissingBean(GenericObjectPoolConfig.class)
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    return new GenericObjectPoolConfig();
  }

  @Bean
  @ConditionalOnProperty(
      value = "redis.cluster-enabled",
      havingValue = "false",
      matchIfMissing = true)
  public JedisClientDelegateFactory jedisClientDelegateFactory(
      Registry registry, ObjectMapper objectMapper, GenericObjectPoolConfig redisPoolConfig) {
    return new JedisClientDelegateFactory(registry, objectMapper, redisPoolConfig);
  }

  @Bean
  @ConditionalOnProperty(
      value = "redis.cluster-enabled",
      havingValue = "false",
      matchIfMissing = true)
  public List<HealthIndicator> jedisClientHealthIndicators(
      List<RedisClientDelegate> redisClientDelegates) {
    return redisClientDelegates.stream()
        .filter(it -> it instanceof JedisClientDelegate)
        .map(it -> JedisHealthIndicatorFactory.build((JedisClientDelegate) it))
        .collect(Collectors.toList());
  }
}
