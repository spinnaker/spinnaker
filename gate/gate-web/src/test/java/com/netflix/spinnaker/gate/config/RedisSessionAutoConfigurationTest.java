/*
 * Copyright 2026 Spinnaker Authors.
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

package com.netflix.spinnaker.gate.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.session.autoconfigure.SessionAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.SessionRepository;

/**
 * Gate's session Redis wiring ({@link RedisConfig}, {@link RedisActionConfig}, {@link
 * PostConnectionConfiguringJedisConnectionFactory}) must coexist with Spring Boot's Redis and
 * session autoconfiguration, which is on the classpath via spring-boot-starter-session-data-redis.
 */
class RedisSessionAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DataRedisAutoConfiguration.class,
                  SessionAutoConfiguration.class,
                  SessionDataRedisAutoConfiguration.class))
          .withUserConfiguration(RedisConfig.class, RedisActionConfig.class)
          .withBean(PostConnectionConfiguringJedisConnectionFactory.class)
          // lazy connections: nothing here needs a live Redis
          .withPropertyValues("redis.connection=redis://:secret@redis.example:6380");

  @Test
  void bootBacksOffAndSpinnakerRedisPropertiesAreUsed() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).hasSingleBean(RedisConnectionFactory.class);
          assertThat(ctx).hasSingleBean(PostConnectionConfiguringJedisConnectionFactory.class);
          assertThat(ctx).hasSingleBean(SessionRepository.class);

          PostConnectionConfiguringJedisConnectionFactory factory =
              ctx.getBean(PostConnectionConfiguringJedisConnectionFactory.class);
          assertThat(factory.getHostName()).isEqualTo("redis.example");
          assertThat(factory.getPort()).isEqualTo(6380);
          assertThat(factory.getPassword()).isEqualTo("secret");
        });
  }
}
