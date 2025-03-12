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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class KubernetesSearchProviderTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              UserConfigurations.of(KubernetesSearchProvider.class, TestConfiguration.class));

  @Test
  void testKubernetesSearchProviderBeanIsPresentByDefault() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(KubernetesSearchProvider.class));
  }

  @Test
  void testKubernetesSearchProviderBeanIsPresentWhenConfiguredInSuchAWay() {
    runner
        .withPropertyValues("kubernetes.search.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(KubernetesSearchProvider.class));
  }

  @Test
  void testKubernetesSearchProviderBeanIsNotPresentWhenConfiguredInSuchAWay() {
    runner
        .withPropertyValues("kubernetes.search.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(KubernetesSearchProvider.class));
  }

  /** test class that supplies beans needed to autowire the KubernetesSearchProvider bean */
  static class TestConfiguration {
    @Bean
    ObjectMapper getObjectMapper() {
      return new ObjectMapper();
    }

    @Bean
    KubernetesCacheUtils getKubernetesCacheUtils() {
      return mock(KubernetesCacheUtils.class);
    }

    @Bean
    KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap() {
      return new KubernetesSpinnakerKindMap(List.of());
    }

    @Bean
    KubernetesAccountResolver kubernetesAccountResolver() {
      return mock(KubernetesAccountResolver.class);
    }
  }
}
