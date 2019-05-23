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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
class KubernetesNamedAccountCredentialsInitializer {
  @Autowired Registry spectatorRegistry
  @Autowired KubectlJobExecutor jobExecutor
  @Autowired KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap

  @Bean
  List<? extends KubernetesNamedAccountCredentials> kubernetesNamedAccountCredentials(
    KubernetesNamedAccountCredentials.CredentialFactory credentialFactory,
    KubernetesConfigurationProperties kubernetesConfigurationProperties,
    AccountCredentialsRepository accountCredentialsRepository) {

    synchronizeKubernetesAccounts(credentialFactory, kubernetesConfigurationProperties,
      null, accountCredentialsRepository)
  }

  private List<? extends KubernetesNamedAccountCredentials> synchronizeKubernetesAccounts(
    KubernetesNamedAccountCredentials.CredentialFactory credentialFactory,
    KubernetesConfigurationProperties kubernetesConfigurationProperties,
    CatsModule catsModule,
    AccountCredentialsRepository accountCredentialsRepository) {
    def (ArrayList<KubernetesConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                         KubernetesNamedAccountCredentials,
                                         kubernetesConfigurationProperties.accounts)

    // TODO(lwander): Modify accounts when their dockerRegistries attribute is updated as well -- need to ask @duftler.
    accountsToAdd.each { KubernetesConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def kubernetesAccount = new KubernetesNamedAccountCredentials(managedAccount, kubernetesSpinnakerKindMap, credentialFactory)

        accountCredentialsRepository.save(managedAccount.name, kubernetesAccount)
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for Kubernetes.", e
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    accountCredentialsRepository.all.findAll {
      it instanceof KubernetesNamedAccountCredentials
    } as List<KubernetesNamedAccountCredentials>
  }
}
