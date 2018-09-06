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

import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
public class CloudFoundryProviderConfig {

  @Bean
  @DependsOn("cloudFoundryAccountCredentials")
  public CloudFoundryProvider cloudFoundryProvider(AccountCredentialsRepository accountCredentialsRepository) {
    CloudFoundryProvider provider = new CloudFoundryProvider(
      Collections.newSetFromMap(new ConcurrentHashMap<>()));
    synchronizeCloudFoundryProvider(provider, accountCredentialsRepository);
    return provider;
  }

  @Bean
  public CloudFoundryProviderSynchronizerTypeWrapper cloudFoundryProviderSynchronizerTypeWrapper() {
    return new CloudFoundryProviderSynchronizerTypeWrapper();
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public CloudFoundryProviderSynchronizer synchronizeCloudFoundryProvider(CloudFoundryProvider cloudFoundryProvider,
                                                                          AccountCredentialsRepository accountCredentialsRepository) {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(cloudFoundryProvider);
    Set<CloudFoundryCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
      CloudFoundryCredentials.class);

    cloudFoundryProvider.getAgents().addAll(allAccounts.stream()
      .map(credentials -> !scheduledAccounts.contains(credentials.getName()) ?
        new CloudFoundryCachingAgent(credentials.getName(), credentials.getClient()) :
        null)
      .filter(Objects::nonNull)
      .collect(Collectors.toList()));

    return new CloudFoundryProviderSynchronizer();
  }

  class CloudFoundryProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    public Class getSynchronizerType() {
      return CloudFoundryProviderSynchronizer.class;
    }
  }

  class CloudFoundryProviderSynchronizer {
  }
}