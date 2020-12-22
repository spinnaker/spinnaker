/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.config;

import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.*;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.poller.Poller;
import java.util.concurrent.ForkJoinPool;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudFoundryProviderConfig {

  @Bean
  public CloudFoundryProvider cloudFoundryProvider() {
    return new CloudFoundryProvider();
  }

  @Bean
  public ForkJoinPool cloudFoundryThreadPool(
      CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties) {
    return new ForkJoinPool(cloudFoundryConfigurationProperties.getApiRequestParallelism());
  }

  @Bean
  @ConditionalOnMissingBean(
      value = CloudFoundryCredentials.class,
      parameterizedContainer = AbstractCredentialsLoader.class)
  public AbstractCredentialsLoader<CloudFoundryCredentials> cloudFoundryCredentialsLoader(
      @Nullable
          CredentialsDefinitionSource<CloudFoundryConfigurationProperties.ManagedAccount>
              cloudFoundryCredentialSource,
      CloudFoundryConfigurationProperties configurationProperties,
      CacheRepository cacheRepository,
      CredentialsRepository<CloudFoundryCredentials> cloudFoundryCredentialsRepository,
      ForkJoinPool cloudFoundryThreadPool) {

    if (cloudFoundryCredentialSource == null) {
      cloudFoundryCredentialSource = configurationProperties::getAccounts;
    }
    return new BasicCredentialsLoader<>(
        cloudFoundryCredentialSource,
        a ->
            new CloudFoundryCredentials(
                a.getName(),
                a.getAppsManagerUri(),
                a.getMetricsUri(),
                a.getApi(),
                a.getUser(),
                a.getPassword(),
                a.getEnvironment(),
                a.isSkipSslValidation(),
                a.getResultsPerPage(),
                cacheRepository,
                a.getPermissions().build(),
                cloudFoundryThreadPool,
                a.getSpaceFilter()),
        cloudFoundryCredentialsRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = CloudFoundryCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<CloudFoundryCredentials> cloudFoundryCredentialsRepository(
      CredentialsLifecycleHandler<CloudFoundryCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(CloudFoundryProvider.PROVIDER_ID, eventHandler);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = CloudFoundryConfigurationProperties.ManagedAccount.class,
      parameterizedContainer = CredentialsDefinitionSource.class)
  public CredentialsInitializerSynchronizable cloudFoundryCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<CloudFoundryCredentials> loader) {
    final Poller<CloudFoundryCredentials> poller = new Poller<>(loader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
