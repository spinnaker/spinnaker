/*
 * Copyright 2026 Spinnaker Contributors
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
package com.netflix.spinnaker.gate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.gate.config.RateLimiterConfig;
import com.netflix.spinnaker.gate.config.RateLimiterConfiguration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

/** Every piece of rate limiting is a bean that backs off when the application defines its own. */
class RateLimiterConfigTest {

  ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Infrastructure.class)
          .withPropertyValues("rate-limit.enabled=true");

  @Test
  void disabledByDefault() {
    new ApplicationContextRunner()
        .withUserConfiguration(Infrastructure.class)
        .run(ctx -> assertThat(ctx).doesNotHaveBean(RateLimiter.class));
  }

  @Test
  void definesTheDefaultBeans() {
    runner
        .withUserConfiguration(RateLimiterConfig.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(RateLimiter.class);
              assertThat(ctx.getBean(RateLimiter.class)).isInstanceOf(RedisRateLimiter.class);
              assertThat(ctx.getBean(RateLimitPrincipalProvider.class))
                  .isInstanceOf(RedisRateLimitPrincipalProvider.class);
              assertThat(ctx.getBean(RateLimitPrincipalResolver.class))
                  .isInstanceOf(DefaultRateLimitPrincipalResolver.class);
              assertThat(ctx).hasSingleBean(RateLimitRuleMatcher.class);
              assertThat(ctx).hasBean("rateLimitingFilter");
            });
  }

  @Test
  void applicationBeansReplaceTheDefaults() {
    runner
        .withUserConfiguration(Overrides.class, RateLimiterConfig.class)
        .run(
            ctx -> {
              assertThat(ctx.getBean(RateLimiter.class)).isSameAs(Overrides.LIMITER);
              assertThat(ctx.getBean(RateLimitPrincipalProvider.class))
                  .isSameAs(Overrides.PROVIDER);
              assertThat(ctx.getBean(RateLimitPrincipalResolver.class))
                  .isSameAs(Overrides.RESOLVER);
              assertThat(ctx.getBean(RateLimitRuleMatcher.class)).isSameAs(Overrides.MATCHER);
            });
  }

  @Configuration
  static class Infrastructure {
    @Bean
    JedisPool jedisPool() {
      return mock(JedisPool.class);
    }

    @Bean
    Registry registry() {
      return new NoopRegistry();
    }

    @Bean
    RateLimiterConfiguration rateLimiterConfiguration() {
      return new RateLimiterConfiguration();
    }
  }

  @Configuration
  static class Overrides {
    static final RateLimiter LIMITER = mock(RateLimiter.class);
    static final RateLimitPrincipalProvider PROVIDER = mock(RateLimitPrincipalProvider.class);
    static final RateLimitPrincipalResolver RESOLVER = request -> Optional.of("custom");
    static final RateLimitRuleMatcher MATCHER = new RateLimitRuleMatcher(List.of());

    @Bean
    RateLimiter customLimiter() {
      return LIMITER;
    }

    @Bean
    RateLimitPrincipalProvider customProvider() {
      return PROVIDER;
    }

    @Bean
    RateLimitPrincipalResolver customResolver() {
      return RESOLVER;
    }

    @Bean
    RateLimitRuleMatcher customMatcher() {
      return MATCHER;
    }
  }
}
