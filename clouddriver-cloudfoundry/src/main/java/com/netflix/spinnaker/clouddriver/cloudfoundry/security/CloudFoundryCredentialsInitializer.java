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

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class CloudFoundryCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Bean
  public List<? extends CloudFoundryCredentials> cloudFoundryAccountCredentials(
      CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository,
      ApplicationContext applicationContext,
      List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {
    return synchronizeCloudFoundryAccounts(
        cloudFoundryConfigurationProperties,
        null,
        accountCredentialsRepository,
        applicationContext,
        providerSynchronizerTypeWrappers);
  }

  @Override
  public String getCredentialsSynchronizationBeanName() {
    return "synchronizeCloudFoundryAccounts";
  }

  @SuppressWarnings("unchecked")
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public List<? extends CloudFoundryCredentials> synchronizeCloudFoundryAccounts(
      CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
      CatsModule catsModule,
      AccountCredentialsRepository accountCredentialsRepository,
      ApplicationContext applicationContext,
      List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {
    List<?> deltas =
        ProviderUtils.calculateAccountDeltas(
            accountCredentialsRepository,
            CloudFoundryCredentials.class,
            cloudFoundryConfigurationProperties.getAccounts());

    List<CloudFoundryConfigurationProperties.ManagedAccount> accountsToAdd =
        (List<CloudFoundryConfigurationProperties.ManagedAccount>) deltas.get(0);
    List<String> namesOfDeletedAccounts = (List<String>) deltas.get(1);

    for (CloudFoundryConfigurationProperties.ManagedAccount managedAccount : accountsToAdd) {
      CloudFoundryCredentials cloudFoundryAccountCredentials =
          new CloudFoundryCredentials(
              managedAccount.getName(),
              managedAccount.getAppsManagerUri(),
              managedAccount.getMetricsUri(),
              managedAccount.getApi(),
              managedAccount.getUser(),
              managedAccount.getPassword(),
              managedAccount.getEnvironment());
      accountCredentialsRepository.save(managedAccount.getName(), cloudFoundryAccountCredentials);
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule);

    if (!accountsToAdd.isEmpty() && catsModule != null) {
      ProviderUtils.synchronizeAgentProviders(applicationContext, providerSynchronizerTypeWrappers);
    }

    return accountCredentialsRepository.getAll().stream()
        .filter(CloudFoundryCredentials.class::isInstance)
        .map(CloudFoundryCredentials.class::cast)
        .collect(Collectors.toList());
  }
}
