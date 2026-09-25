/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.pubsub.redis.config;

import com.netflix.spinnaker.kork.pubsub.redis.streams.DefaultRedisStreamMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageAcknowledger;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires up Redis-backed pub/sub: a Streams-based competing-consumer ("single delivery")
 * implementation, and a native-Pub/Sub-based broadcast implementation. The provider beans ({@code
 * RedisStreamsSubscriberProvider}, {@code RedisStreamsPublisherProvider}, {@code
 * RedisBroadcastProvider}) are discovered via the component scan below rather than individually
 * {@code @Bean}-declared here, matching {@code cats-pubsub}'s {@code PubSubSchedulerConfig}.
 *
 * <p>This must be a plain {@code @Import}, NOT {@code @ImportAutoConfiguration}: a host app that
 * excludes {@code RedisAutoConfiguration} (e.g. because it talks to Redis through a different
 * client elsewhere) has that exclusion merged across its whole auto-configuration import group, and
 * an {@code @ImportAutoConfiguration} of an excluded class would be silently dropped. A direct
 * import bypasses the group; the conditions inside the imported classes still apply, so a host app
 * that already defines its own {@code RedisConnectionFactory}/{@code StringRedisTemplate} (or its
 * own Spring Integration baseline) is unaffected, since those auto-configurations' own bean methods
 * are {@code @ConditionalOnMissingBean}.
 */
@Configuration
@ComponentScan(
    basePackages = {
      "com.netflix.spinnaker.kork.pubsub.redis.streams",
      "com.netflix.spinnaker.kork.pubsub.redis.broadcast"
    })
@ConditionalOnProperty({"pubsub.enabled", "pubsub.redis.enabled"})
@EnableConfigurationProperties(RedisPubsubProperties.class)
@Import({
  RedisAutoConfiguration.class,
  RedisRepositoriesAutoConfiguration.class,
  IntegrationAutoConfiguration.class
})
public class RedisPubsubConfig {
  public static final String SYSTEM = "redis";

  @Valid @Autowired private RedisPubsubProperties redisPubsubProperties;

  @Bean
  @ConditionalOnMissingBean(RedisStreamMessageAcknowledger.class)
  RedisStreamMessageAcknowledger defaultRedisStreamMessageAcknowledger(
      StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
    return new DefaultRedisStreamMessageAcknowledger(redisTemplate, meterRegistry);
  }
}
