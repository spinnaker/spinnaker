/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRefreshListener;
import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsLoader;
import com.netflix.spinnaker.credentials.poller.PollerConfiguration;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class AccountDefinitionRefreshConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              PropertiesConfiguration.class,
              AccountDefinitionPollingConfiguration.class,
              AccountDefinitionPubsubConfiguration.class)
          .withPropertyValues(
              "credentials.poller.enabled=true",
              "credentials.poller.types.default.reload-frequency-ms=1000")
          .withBean(CredentialsLoader.class, this::loader);

  @Test
  void defaultsToTheExistingPollingBehaviorWithoutRedis() {
    runner.run(
        context -> {
          assertThat(context)
              .hasSingleBean(PollerConfiguration.class)
              .doesNotHaveBean(AccountDefinitionRefreshListener.class)
              .doesNotHaveBean(RedisPubsub.class);
          ScheduledTaskRegistrar tasks = new ScheduledTaskRegistrar();
          context.getBean(PollerConfiguration.class).configureTasks(tasks);
          assertThat(tasks.getTriggerTaskList()).hasSize(1);
        });
  }

  @Test
  void optingInReplacesPollingOnlyWhenSqlAccountStorageIsEnabled() {
    runner
        .withPropertyValues("sql.enabled=true", "account.storage.pubsub.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(AccountDefinitionRefreshListener.class)
                    .hasSingleBean(RedisPubsub.class)
                    .doesNotHaveBean(PollerConfiguration.class));
  }

  @Test
  void fallsBackToPollingWhenSqlOrAccountStorageIsDisabled() {
    runner
        .withPropertyValues("sql.enabled=false", "account.storage.pubsub.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(PollerConfiguration.class)
                    .doesNotHaveBean(AccountDefinitionRefreshListener.class));
    runner
        .withPropertyValues(
            "sql.enabled=true",
            "account.storage.enabled=false",
            "account.storage.pubsub.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(PollerConfiguration.class)
                    .doesNotHaveBean(AccountDefinitionRefreshListener.class));
  }

  @Test
  void startupLoadingRemainsEnabledInBothDeliveryConfigurations() {
    ApplicationContextRunner startup =
        new ApplicationContextRunner()
            .withUserConfiguration(
                PropertiesConfiguration.class,
                AccountDefinitionPollingConfiguration.class,
                AccountDefinitionPubsubConfiguration.class)
            .withBean(StartupLoader.class, StartupLoader::new);
    startup.run(context -> assertThat(context.getBean(StartupLoader.class).loads).hasValue(1));
    startup
        .withPropertyValues("sql.enabled=true", "account.storage.pubsub.enabled=true")
        .run(context -> assertThat(context.getBean(StartupLoader.class).loads).hasValue(1));
  }

  static class StartupLoader extends AbstractCredentialsLoader<Credentials> {
    final AtomicInteger loads = new AtomicInteger();

    StartupLoader() {
      super(mock(CredentialsRepository.class));
    }

    @Override
    public void load() {
      loads.incrementAndGet();
    }
  }

  @SuppressWarnings("unchecked")
  private CredentialsLoader<Credentials> loader() {
    CredentialsRepository<Credentials> repository = mock(CredentialsRepository.class);
    when(repository.getType()).thenReturn("test");
    CredentialsLoader<Credentials> loader = mock(CredentialsLoader.class);
    when(loader.getCredentialsRepository()).thenReturn(repository);
    return loader;
  }

  @Configuration
  @EnableConfigurationProperties(AccountDefinitionConfiguration.Properties.class)
  static class PropertiesConfiguration {}
}
