/*
 * Copyright 2024 OpsMx, Inc.
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.session.data.redis.config.ConfigureRedisAction;

class RedisConfigTest {

  EmbeddedRedis redis = embeddedRedis();
  String conn = "redis.connection=redis://" + redis.getHost() + ":" + redis.getPort();

  @Test
  public void testCircularDependenciesException() {
    ApplicationContextRunner applicationContextRunner =
        new ApplicationContextRunner()
            .withPropertyValues(conn)
            .withUserConfiguration(RedisConfig.class, RedisActionConfig.class)
            .withBean(PostConnectionConfiguringJedisConnectionFactory.class);
    assertDoesNotThrow(
        () ->
            applicationContextRunner.run(
                ctx -> assertThat(ctx).hasSingleBean(ConfigureRedisAction.class)));
  }

  @Test
  public void testCircularDependenciesExceptionSecure() {
    ApplicationContextRunner applicationContextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class, RedisActionConfig.class)
            .withBean(PostConnectionConfiguringJedisConnectionFactory.class)
            .withPropertyValues("redis.configuration.secure", "true")
            .withPropertyValues(conn);

    assertDoesNotThrow(
        () ->
            applicationContextRunner.run(
                ctx -> assertThat(ctx).getBeans(ConfigureRedisAction.class).hasSize(2)));
  }

  EmbeddedRedis embeddedRedis() {
    EmbeddedRedis redis = EmbeddedRedis.embed();
    redis.getJedis().connect();
    redis.getJedis().ping();
    return redis;
  }
}
