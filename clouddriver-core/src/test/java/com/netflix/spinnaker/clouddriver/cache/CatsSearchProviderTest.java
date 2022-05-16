/*
 * Copyright 2021 Salesforce.com, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class CatsSearchProviderTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues("caching.write-enabled=false", "redis.enabled:false")
          .withConfiguration(UserConfigurations.of(CacheConfig.class, TestConfiguration.class))
          .withAllowBeanDefinitionOverriding(true);

  @Test
  void testCatsSearchProviderBeanIsPresentByDefault() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(CatsSearchProvider.class));
  }

  @Test
  void testCatsSearchProviderBeanIsPresentWhenConfiguredInSuchAWay() {
    runner
        .withPropertyValues("caching.search.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(CatsSearchProvider.class));
  }

  @Test
  void testCatsSearchProviderBeanIsNotPresentWhenConfiguredInSuchAWay() {
    runner
        .withPropertyValues("caching.search.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(CatsSearchProvider.class));
  }

  /**
   * test class that supplies the minimum set of beans needed to autowire the CatsSearchProvider
   * bean and other required beans in the CacheConfig class
   */
  static class TestConfiguration {
    @Bean
    CatsInMemorySearchProperties catsInMemorySearchProperties() {
      return new CatsInMemorySearchProperties();
    }

    @Bean
    Cache cache() {
      return new InMemoryCache();
    }

    @Bean
    Registry registry() {
      return new NoopRegistry();
    }
  }
}
