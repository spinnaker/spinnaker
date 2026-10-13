/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.pubsub.config;

import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuration for Redis Streams messaging.
 *
 * <p>Explicitly imported by applications requiring Redis pubsub. This configuration uses an
 * application-supplied {@link StringRedisTemplate} (for example, provided by the application's own
 * Spring Boot Redis auto-configuration or custom bean definition) and imports {@link PubsubConfig}.
 * It does not import Redis auto-configuration directly to avoid activating Redis connections or
 * health indicators in applications or modules that only use local queues or other pubsub
 * transports.
 */
@Configuration
@Import(PubsubConfig.class)
public class RedisPubsubConfig {
  @Bean
  @ConditionalOnMissingBean
  public RedisPubsub redisPubsub(
      StringRedisTemplate redis, PubsubPublishers publishers, PubsubSubscribers subscribers) {
    return new RedisPubsub(redis, publishers, subscribers);
  }
}
