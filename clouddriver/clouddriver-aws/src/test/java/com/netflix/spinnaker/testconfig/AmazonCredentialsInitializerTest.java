/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.testconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookup;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookupFactory;
import com.netflix.spinnaker.clouddriver.aws.security.AWSCredentialsProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentialsInitializer;
import com.netflix.spinnaker.kork.aws.AwsComponents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

public class AmazonCredentialsInitializerTest {

  // minimal set of beans necessary to initialize all the beans in AmazonCredentialsInitializer
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(NoopRegistry.class)
          .withConfiguration(
              UserConfigurations.of(AwsComponents.class, TestCommonDependencyConfiguration.class));

  @Test
  void testAmazonCredentialsInitializerBasicFunctionality() {
    runner.run(
        ctx -> {
          // FIXME: expect the context to start with a CredentialsParser bean
          //
          // assertThat(ctx).hasSingleBean(CredentialsParser.class);
          Throwable thrown = ctx.getStartupFailure();
          assertThat(thrown).isInstanceOf(UnsatisfiedDependencyException.class);
          assertThat(thrown)
              .hasMessage(
                  "Error creating bean with name 'amazonCredentialsLoader' defined in class path resource [com/netflix/spinnaker/clouddriver/aws/security/AmazonCredentialsInitializer.class]: Unsatisfied dependency expressed through method 'amazonCredentialsLoader' parameter 0; nested exception is org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.netflix.spinnaker.credentials.definition.CredentialsParser<com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration$Account, com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials>' available: expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {}");
          assertThat(thrown).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
          assertThat(thrown)
              .hasRootCauseMessage(
                  "No qualifying bean of type 'com.netflix.spinnaker.credentials.definition.CredentialsParser<com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration$Account, com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials>' available: expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {}");
        });
  }

  /**
   * Bean definitions that allow beans in {@link AmazonCredentialsInitializer} to initialize
   * correctly.
   */
  @TestConfiguration
  @ComponentScan({"com.netflix.spinnaker.clouddriver.aws.security"})
  static class TestCommonDependencyConfiguration {
    @Bean
    AmazonClientProvider amazonClientProvider() {
      return new AmazonClientProvider.Builder().build();
    }

    @Bean
    AWSAccountInfoLookup awsAccountInfoLookup() {
      return mock(AWSAccountInfoLookup.class);
    }

    @Bean
    AWSAccountInfoLookupFactory awsAccountInfoLookupFactory() {
      return mock(AWSAccountInfoLookupFactory.class);
    }

    @Bean
    AWSCredentialsProviderFactory awsCredentialsProviderFactory() {
      return mock(AWSCredentialsProviderFactory.class);
    }
  }
}
