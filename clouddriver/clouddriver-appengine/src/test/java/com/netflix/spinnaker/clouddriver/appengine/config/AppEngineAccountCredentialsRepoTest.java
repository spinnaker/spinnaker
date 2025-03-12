package com.netflix.spinnaker.clouddriver.appengine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class AppEngineAccountCredentialsRepoTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              UserConfigurations.of(
                  AppengineCredentialsConfiguration.class, TestConfiguration.class));

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
    AppengineConfigurationProperties getAppengineConfigurationProperties() {
      return mock(AppengineConfigurationProperties.class);
    }

    @Bean
    ConfigFileService getConfigFileService() {
      return mock(ConfigFileService.class);
    }

    @Bean
    AppengineJobExecutor getAppengineExecutor() {
      return mock(AppengineJobExecutor.class);
    }

    @Bean
    JobExecutor getJobExecutor() {
      return mock(JobExecutor.class);
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
