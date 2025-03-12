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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.application.view.AzureApplicationProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.cluster.view.AzureClusterProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class AzureLoadBalancerProviderTest {

  private static class AzureTestConfig {

    @Bean
    AzureCloudProvider azureCloudProvider() {
      return mock(AzureCloudProvider.class);
    }

    @Bean
    AzureClusterProvider azureClusterProvider() {
      return mock(AzureClusterProvider.class);
    }

    @Bean
    AzureApplicationProvider azureApplicationProvider() {
      return mock(AzureApplicationProvider.class);
    }

    @Bean
    Cache cache() {
      return mock(Cache.class);
    }

    @Bean
    ObjectMapper getObjectMapper() {
      return new ObjectMapper();
    }
  }

  private final ApplicationContextRunner applicationContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(UserConfigurations.of(AzureTestConfig.class))
          .withBean(AzureLoadBalancerProvider.class);

  /**
   * The AzureLoadBalancerProvider class previously had a self-reference, which resulted in a
   * circular reference exception. The intention of this test is to detect that exception scenario,
   * without enabling the Azure provider.
   */
  @Test
  public void testCircularDependenciesException() {
    assertDoesNotThrow(
        () ->
            applicationContextRunner.run(
                ctx -> assertThat(ctx).hasSingleBean(AzureLoadBalancerProvider.class)));
  }
}
