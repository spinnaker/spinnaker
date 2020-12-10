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
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.poller.Poller;
import java.util.concurrent.ForkJoinPool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
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
  public CredentialsTypeBaseConfiguration<
          CloudFoundryCredentials, CloudFoundryConfigurationProperties.ManagedAccount>
      cloudfoundryCredentials(
          ApplicationContext applicationContext,
          CloudFoundryConfigurationProperties configurationProperties,
          CacheRepository cacheRepository,
          ForkJoinPool cloudFoundryThreadPool) {
    return new CredentialsTypeBaseConfiguration<>(
        applicationContext,
        CredentialsTypeProperties
            .<CloudFoundryCredentials, CloudFoundryConfigurationProperties.ManagedAccount>builder()
            .type(CloudFoundryProvider.PROVIDER_ID)
            .credentialsClass(CloudFoundryCredentials.class)
            .credentialsDefinitionClass(CloudFoundryConfigurationProperties.ManagedAccount.class)
            .defaultCredentialsSource(configurationProperties::getAccounts)
            .credentialsParser(
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
                        a.getSpaceFilter()))
            .build());
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
