/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.google.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.deploy.converters.AbandonAndDecrementGoogleServerGroupAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.config.GoogleConfiguration;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class GoogleCredentialsConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              UserConfigurations.of(
                  GoogleCredentialsConfiguration.class,
                  AbandonAndDecrementGoogleServerGroupAtomicOperationConverter.class,
                  TestConfiguration.class));

  @Test
  void testCredentialsRepositoryBeanIsPresent() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(CredentialsRepository.class));
  }

  static class TestConfiguration {
    @Bean
    ObjectMapper getObjectMapper() {
      return new ObjectMapper();
    }

    @Bean
    CredentialsLifecycleHandler getCredentialsLifecycleHandler() {
      return mock(CredentialsLifecycleHandler.class);
    }

    @Bean
    NamerRegistry getNamerRegistry() {
      return mock(NamerRegistry.class);
    }

    @Bean
    GoogleConfigurationProperties getGoogleConfigurationProperties() {
      return mock(GoogleConfigurationProperties.class);
    }

    @Bean
    ConfigFileService getConfigFileService() {
      return mock(ConfigFileService.class);
    }

    @Bean
    GoogleConfiguration.DeployDefaults getGoogleConfigurationDeployDefaults() {
      return mock(GoogleConfiguration.DeployDefaults.class);
    }

    @Bean
    GoogleExecutor getGoogleExecutor() {
      return mock(GoogleExecutor.class);
    }

    @Bean
    Registry getRegistry() {
      return mock(Registry.class);
    }

    @Bean
    String getClouddriverUserAgentApplicationName() {
      return "clouddriverUserAgentApplicationName";
    }

    @Bean
    ServiceClientProvider getServiceClientProvider() {
      return mock(ServiceClientProvider.class);
    }
  }
}
