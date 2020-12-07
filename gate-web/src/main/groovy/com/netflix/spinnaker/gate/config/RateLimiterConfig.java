/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.gate.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.gate.ratelimit.RateLimitPrincipalProvider;
import com.netflix.spinnaker.gate.ratelimit.RateLimiter;
import com.netflix.spinnaker.gate.ratelimit.RateLimitingFilter;
import com.netflix.spinnaker.gate.ratelimit.RedisRateLimitPrincipalProvider;
import com.netflix.spinnaker.gate.ratelimit.RedisRateLimiter;
import com.netflix.spinnaker.gate.ratelimit.StaticRateLimitPrincipalProvider;
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

@Configuration
@ConditionalOnExpression("${rate-limit.enabled:false}")
public class RateLimiterConfig {

  @Autowired(required = false)
  RateLimiterConfiguration rateLimiterConfiguration;

  @Bean
  @ConditionalOnExpression("${rate-limit.redis.enabled:false}")
  RateLimiter redisRateLimiter(JedisPool jedisPool) {
    return new RedisRateLimiter(jedisPool);
  }

  @Bean
  @ConditionalOnExpression("${rate-limit.redis.enabled:false}")
  RateLimitPrincipalProvider redisRateLimiterPrincipalProvider(JedisPool jedisPool) {
    return new RedisRateLimitPrincipalProvider(jedisPool, rateLimiterConfiguration);
  }

  @Bean
  @ConditionalOnMissingBean(RateLimitPrincipalProvider.class)
  RateLimitPrincipalProvider staticRateLimiterPrincipalProvider() {
    return new StaticRateLimitPrincipalProvider(rateLimiterConfiguration);
  }

  @Bean
  FilterRegistrationBean rateLimitingFilter(
      RateLimiter rateLimiter,
      Registry registry,
      RateLimitPrincipalProvider rateLimitPrincipalProvider,
      Optional<List<RequestIdentityExtractor>> requestIdentityExtractors) {
    FilterRegistrationBean frb =
        new FilterRegistrationBean(
            new RateLimitingFilter(
                rateLimiter,
                registry,
                rateLimitPrincipalProvider,
                requestIdentityExtractors.orElse(null)));

    frb.setOrder(rateLimiterConfiguration.getFilterOrder());
    return frb;
  }
}
