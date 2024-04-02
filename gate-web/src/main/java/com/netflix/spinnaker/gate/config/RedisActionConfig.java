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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.session.data.redis.config.ConfigureRedisAction;

@Configuration
public class RedisActionConfig {

  /**
   * Always disable the ConfigureRedisAction that Spring Boot uses internally. Instead we use one
   * qualified with @ConnectionPostProcessor. See {@link
   * PostConnectionConfiguringJedisConnectionFactory}.
   */
  @Bean
  @Primary
  public ConfigureRedisAction springBootConfigureRedisAction() {
    return ConfigureRedisAction.NO_OP;
  }

  @Bean
  @ConditionalOnProperty("redis.configuration.secure")
  @PostConnectionConfiguringJedisConnectionFactory.ConnectionPostProcessor
  public ConfigureRedisAction connectionPostProcessorConfigureRedisAction() {
    return ConfigureRedisAction.NO_OP;
  }
}
