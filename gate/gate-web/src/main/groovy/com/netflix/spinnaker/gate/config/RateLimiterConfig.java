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
import com.netflix.spinnaker.gate.ratelimit.DefaultRateLimitPrincipalResolver;
import com.netflix.spinnaker.gate.ratelimit.RateLimitPrincipalProvider;
import com.netflix.spinnaker.gate.ratelimit.RateLimitPrincipalResolver;
import com.netflix.spinnaker.gate.ratelimit.RateLimitRuleMatcher;
import com.netflix.spinnaker.gate.ratelimit.RateLimiter;
import com.netflix.spinnaker.gate.ratelimit.RateLimitingFilter;
import com.netflix.spinnaker.gate.ratelimit.RedisRateLimitPrincipalProvider;
import com.netflix.spinnaker.gate.ratelimit.RedisRateLimiter;
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import com.netflix.spinnaker.gate.services.PermissionService;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Rate limiting is shared across gate instances through Redis (the same {@link JedisPool} gate uses
 * for its other Redis-backed features), so Redis is required whenever {@code rate-limit.enabled} is
 * set.
 *
 * <p>Every component is a bean that backs off when the application defines its own (e.g. from a
 * plugin), so rate limiting can be customized piece by piece: the {@link RateLimiter} storage, the
 * {@link RateLimitPrincipalProvider} that supplies per-principal limits, the {@link
 * RateLimitPrincipalResolver} that decides who a request belongs to, and the {@link
 * RateLimitRuleMatcher}.
 */
@Configuration
@ConditionalOnExpression("${rate-limit.enabled:false}")
@Slf4j
public class RateLimiterConfig {

  @Bean
  @ConditionalOnMissingBean
  RateLimiter redisRateLimiter(JedisPool jedisPool, RateLimiterConfiguration configuration) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.ping();
    } catch (RuntimeException e) {
      log.error(
          "rate-limit.enabled is true but redis is not reachable via redis.connection; rate limits"
              + " are shared through redis and requests will NOT be limited until it is reachable",
          e);
    }

    log.info(
        "Rate limiting enabled (learning: {}, capacity: {}, rateSeconds: {}, rules: {})",
        configuration.isLearning(),
        configuration.getCapacity(),
        configuration.getRateSeconds(),
        configuration.getRules().size());
    if (configuration.isLearning()) {
      log.info(
          "Rate limiting is in learning mode: over-limit requests are logged but not rejected."
              + " Set rate-limit.learning=false or list principals under rate-limit.enforcing to"
              + " return 429s.");
    }

    return new RedisRateLimiter(jedisPool);
  }

  @Bean
  @ConditionalOnMissingBean
  RateLimitPrincipalProvider redisRateLimiterPrincipalProvider(
      JedisPool jedisPool, RateLimiterConfiguration configuration) {
    return new RedisRateLimitPrincipalProvider(jedisPool, configuration);
  }

  @Bean
  @ConditionalOnMissingBean
  RateLimitRuleMatcher rateLimitRuleMatcher(RateLimiterConfiguration configuration) {
    return new RateLimitRuleMatcher(
        configuration.getRules(), configuration.getExcludePathPatterns());
  }

  @Bean
  @ConditionalOnMissingBean
  RateLimitPrincipalResolver rateLimitPrincipalResolver(
      Optional<List<RequestIdentityExtractor>> requestIdentityExtractors,
      Optional<PermissionService> permissionService) {
    return new DefaultRateLimitPrincipalResolver(
        requestIdentityExtractors.orElse(null), permissionService.orElse(null));
  }

  @Bean
  @ConditionalOnMissingBean(name = "rateLimitingFilter")
  FilterRegistrationBean<RateLimitingFilter> rateLimitingFilter(
      RateLimiter rateLimiter,
      Registry registry,
      RateLimitPrincipalProvider rateLimitPrincipalProvider,
      RateLimitPrincipalResolver rateLimitPrincipalResolver,
      RateLimitRuleMatcher rateLimitRuleMatcher,
      RateLimiterConfiguration configuration) {
    var frb =
        new FilterRegistrationBean<>(
            new RateLimitingFilter(
                rateLimiter,
                registry,
                rateLimitPrincipalProvider,
                rateLimitPrincipalResolver,
                rateLimitRuleMatcher));

    frb.setOrder(configuration.getFilterOrder());
    return frb;
  }
}
