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
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.aws.AwsComponents;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
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
          assertThat(ctx).hasSingleBean(CredentialsParser.class);
        });
  }

  @Test
  void testBeansForExternalAccountStorageAreAvailableByDefault() {
    runner
        .withPropertyValues("account.storage.enabled:true", "account.storage.aws.enabled:true")
        .withConfiguration(
            UserConfigurations.of(TestExternalAccountStorageDependencyConfiguration.class))
        .run(
            ctx -> {
              // FIXME: once implemented, an AccountDefinitionSource bean is present in the context
              // assertThat(ctx).hasSingleBean(AccountDefinitionSource.class);
              assertThat(ctx).doesNotHaveBean(AccountDefinitionSource.class);
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

  @TestConfiguration
  static class TestExternalAccountStorageDependencyConfiguration {
    @Bean
    AccountDefinitionRepository accountDefinitionRepository() {
      return new TestAccountDefinitionRepository();
    }

    @NonnullByDefault
    static class TestAccountDefinitionRepository implements AccountDefinitionRepository {
      @Nullable
      @Override
      public CredentialsDefinition getByName(String name) {
        return null;
      }

      @Override
      public List<? extends CredentialsDefinition> listByType(
          String typeName, int limit, @Nullable String startingAccountName) {
        return new ArrayList<>();
      }

      @Override
      public List<? extends CredentialsDefinition> listByType(String typeName) {
        return new ArrayList<>();
      }

      @Override
      public void create(CredentialsDefinition definition) {}

      @Override
      public void save(CredentialsDefinition definition) {}

      @Override
      public void update(CredentialsDefinition definition) {}

      @Override
      public void delete(String name) {}

      @Override
      public List<Revision> revisionHistory(String name) {
        return new ArrayList<>();
      }
    }
  }
}
