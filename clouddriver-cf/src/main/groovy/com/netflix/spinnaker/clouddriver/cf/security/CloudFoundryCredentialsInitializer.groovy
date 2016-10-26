/*
 * Copyright 2015-2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
class CloudFoundryCredentialsInitializer implements CredentialsInitializerSynchronizable  {

  @Bean
  List<? extends CloudFoundryAccountCredentials> cloudFoundryAccountCredentials(CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
                                                                                AccountCredentialsRepository accountCredentialsRepository,
                                                                                ApplicationContext applicationContext,
                                                                                List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {
    synchronizeCloudFoundryAccounts(cloudFoundryConfigurationProperties, accountCredentialsRepository, null, applicationContext, providerSynchronizerTypeWrappers)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeCloudFoundryAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<? extends CloudFoundryAccountCredentials> synchronizeCloudFoundryAccounts(CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
                                                                                 AccountCredentialsRepository accountCredentialsRepository,
                                                                                 CatsModule catsModule,
                                                                                 ApplicationContext applicationContext,
                                                                                 List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {
    def (ArrayList<CloudFoundryAccountCredentials> accountsToAdd, List<String> namesOfDeletedAccounts) =
      ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, CloudFoundryAccountCredentials,
          cloudFoundryConfigurationProperties.accounts)

    accountsToAdd.each { CloudFoundryAccountCredentials account ->
      accountCredentialsRepository.save(account.name, account)
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(applicationContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof CloudFoundryAccountCredentials
    } as List<CloudFoundryAccountCredentials>
  }

}
