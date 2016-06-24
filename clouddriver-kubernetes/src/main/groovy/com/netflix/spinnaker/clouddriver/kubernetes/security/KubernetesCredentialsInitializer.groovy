/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Configuration
class KubernetesCredentialsInitializer implements CredentialsInitializerSynchronizable {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  ApplicationContext appContext;

  @Autowired
  KubernetesConfiguration configuration

  @Autowired
  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers

  @Bean
  List<? extends KubernetesNamedAccountCredentials> kubernetesNamedAccountCredentials(
    KubernetesConfigurationProperties kubernetesConfigurationProperties) {
    synchronizeKubernetesAccounts(kubernetesConfigurationProperties, null)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeKubernetesAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("dockerRegistryNamedAccountCredentials")
  List<?> synchronizeKubernetesAccounts(KubernetesConfigurationProperties kubernetesConfigurationProperties, CatsModule catsModule) {
    def (ArrayList<KubernetesConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                         KubernetesNamedAccountCredentials,
                                         kubernetesConfigurationProperties.accounts)

    // TODO(lwander): Modify accounts when their dockerRegistries attribute is updated as well -- need to ask @duftler.
    accountsToAdd.each { KubernetesConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def kubernetesAccount = new KubernetesNamedAccountCredentials.Builder()
          .accountCredentialsRepository(accountCredentialsRepository)
          .userAgent(configuration.kubernetesApplicationName())
          .name(managedAccount.name)
          .environment(managedAccount.environment ?: managedAccount.name)
          .accountType(managedAccount.accountType ?: managedAccount.name)
          .context(managedAccount.context)
          .cluster(managedAccount.cluster)
          .user(managedAccount.user)
          .kubeconfigFile(managedAccount.kubeconfigFile)
          .namespaces(managedAccount.namespaces)
          .dockerRegistries(managedAccount.dockerRegistries)
          .requiredGroupMembership(managedAccount.requiredGroupMembership)
          .build()

        accountCredentialsRepository.save(managedAccount.name, kubernetesAccount)
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for Kubernetes.", e
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(appContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof KubernetesNamedAccountCredentials
    } as List
  }
}
