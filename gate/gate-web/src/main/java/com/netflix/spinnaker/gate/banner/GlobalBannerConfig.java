/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.banner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import redis.clients.jedis.JedisPool;

/**
 * Registers all global-banner beans when {@code global-banner.enabled=true}. All banner state is
 * stored in Redis via {@link RedisBannerRepository}; no Front50 or Echo dependency.
 *
 * <p>{@link EnableScheduling} activates the {@link GlobalBannerService#refreshBannerData()} task
 * that keeps the in-process active-banner cache up to date.
 */
@Configuration
@ConditionalOnProperty("global-banner.enabled")
@EnableConfigurationProperties(GlobalBannerProperties.class)
@EnableScheduling
public class GlobalBannerConfig {

  @Bean
  public RedisBannerRepository redisBannerRepository(
      JedisPool jedisPool, ObjectMapper objectMapper, GlobalBannerProperties properties) {
    return new RedisBannerRepository(jedisPool, objectMapper, properties.getRedisKeyPrefix());
  }

  @Bean
  public GlobalBannerService globalBannerService(RedisBannerRepository redisBannerRepository) {
    return new GlobalBannerService(redisBannerRepository);
  }
}
