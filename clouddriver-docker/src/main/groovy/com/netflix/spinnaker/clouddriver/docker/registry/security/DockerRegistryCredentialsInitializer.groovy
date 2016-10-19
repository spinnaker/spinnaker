/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.docker.registry.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.docker.registry.config.DockerRegistryConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Configuration
class DockerRegistryCredentialsInitializer implements CredentialsInitializerSynchronizable {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  ApplicationContext appContext;

  @Autowired
  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers

  @Bean
  List<? extends DockerRegistryNamedAccountCredentials> dockerRegistryNamedAccountCredentials(
    DockerRegistryConfigurationProperties dockerRegistryConfigurationProperties) {
    synchronizeDockerRegistryAccounts(dockerRegistryConfigurationProperties, null)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeDockerRegistryAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<?> synchronizeDockerRegistryAccounts(DockerRegistryConfigurationProperties dockerRegistryConfigurationProperties, CatsModule catsModule) {
    def (ArrayList<DockerRegistryConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, DockerRegistryNamedAccountCredentials,
      dockerRegistryConfigurationProperties.accounts)

    accountsToAdd.each { DockerRegistryConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def dockerRegistryAccount = (new DockerRegistryNamedAccountCredentials.Builder())
          .accountName(managedAccount.name)
          .environment(managedAccount.environment ?: managedAccount.name)
          .accountType(managedAccount.accountType ?: managedAccount.name)
          .address(managedAccount.address)
          .password(managedAccount.password)
          .username(managedAccount.username)
          .email(managedAccount.email)
          .passwordFile(managedAccount.passwordFile)
          .dockerconfigFile(managedAccount.dockerconfigFile)
          .cacheThreads(managedAccount.cacheThreads)
          .clientTimeoutMillis(managedAccount.clientTimeoutMillis)
          .paginateSize(managedAccount.paginateSize)
          .trackDigests(managedAccount.trackDigests)
          .repositories(managedAccount.repositories)
          .skip(managedAccount.skip)
          .build()

        accountCredentialsRepository.save(managedAccount.name, dockerRegistryAccount)
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for DockerRegistry.", e
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(appContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof DockerRegistryNamedAccountCredentials
    } as List
  }
}
