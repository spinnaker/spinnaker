/*
 * Copyright 2026 Spinnaker Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.fiat.providers.ArtifactAccountResourceProvider;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionProvider;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionSource;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverArtifactAccountLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class ArtifactAccountToggleTest {

  private static final String SOURCE_ENABLED =
      "auth.permissions.source.artifact-account.resource.enabled";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              DefaultResourcePermissionConfig.class,
              ArtifactAccountResourceProvider.class,
              LoaderConfig.class);

  @Test
  void artifactAccountsAreAResourceByDefault() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(ArtifactAccountResourceProvider.class);
          assertThat(context).hasBean("artifactAccountResourcePermissionSource");
          assertThat(context).hasBean("defaultArtifactAccountPermissionProvider");
        });
  }

  @Test
  void disablingTheSourceStartsWithoutAnyArtifactAccountResource() {
    runner
        .withPropertyValues(SOURCE_ENABLED + "=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ArtifactAccountResourceProvider.class);
              assertThat(context.getBeansOfType(ResourcePermissionProvider.class).keySet())
                  .doesNotContain("defaultArtifactAccountPermissionProvider");
              assertThat(context.getBeansOfType(ResourcePermissionSource.class).keySet())
                  .doesNotContain("artifactAccountResourcePermissionSource");
            });
  }

  static class LoaderConfig {
    @Bean
    ProviderCacheConfig providerCacheConfig() {
      return new ProviderCacheConfig();
    }

    @Bean
    @ConditionalOnProperty(value = SOURCE_ENABLED, matchIfMissing = true)
    ClouddriverArtifactAccountLoader clouddriverArtifactAccountLoader() {
      return mock(ClouddriverArtifactAccountLoader.class);
    }
  }
}
