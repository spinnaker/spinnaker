/*
 * Copyright 2017 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientCompositeKey
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

@Slf4j
@Configuration
class DcosCredentialsInitializer implements CredentialsInitializerSynchronizable {
  private final static LOGGER = LoggerFactory.getLogger(DcosCredentialsInitializer)

  @Bean
  List<? extends DcosAccountCredentials> dcosCredentials(String clouddriverUserAgentApplicationName,
                                                         DcosConfigurationProperties dcosConfigurationProperties,
                                                         ApplicationContext applicationContext,
                                                         AccountCredentialsRepository accountCredentialsRepository,
                                                         List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {

    synchronizeDcosAccounts(clouddriverUserAgentApplicationName, dcosConfigurationProperties, null, applicationContext, accountCredentialsRepository, providerSynchronizerTypeWrappers)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeDcosAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("dockerRegistryNamedAccountCredentials")
  List<? extends DcosAccountCredentials> synchronizeDcosAccounts(String clouddriverUserAgentApplicationName,
                                                                 DcosConfigurationProperties dcosConfigurationProperties,
                                                                 CatsModule catsModule,
                                                                 ApplicationContext applicationContext,
                                                                 AccountCredentialsRepository accountCredentialsRepository,
                                                                 List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {

    // TODO what to do with clouddriverUserAgentApplicationName?
    Map<String, DcosConfigurationProperties.Cluster> clusterMap = new HashMap<>()

    for (DcosConfigurationProperties.Cluster cluster in dcosConfigurationProperties.clusters) {
      clusterMap.put(cluster.name, cluster)
    }

    def (ArrayList<DcosConfigurationProperties.Account> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                         DcosAccountCredentials,
                                         dcosConfigurationProperties.accounts)

    accountsToAdd.each { DcosConfigurationProperties.Account account ->
      try {
        List<DcosClusterCredentials> clusterCredentials = new ArrayList<>()

        for (DcosConfigurationProperties.ClusterCredential clusterConfig in account.clusters) {
          DcosConfigurationProperties.Cluster cluster = clusterMap.get(clusterConfig.name)

          if (cluster == null) {
            LOGGER.warn("Cluster [${cluster.name}] could not be found in the list of configured clusters for account [${account}]")
            continue
          }

          def key = DcosClientCompositeKey.buildFromVerbose(account.name, cluster.name)

          if (!key.isPresent()) {
            LOGGER.warn("Something went wrong with the composite key creation for account [${account.name}] and cluster [${cluster.name}]")
            continue
          }

          def dockerRegistries = cluster.dockerRegistries ? cluster.dockerRegistries : account.dockerRegistries

          clusterCredentials.add(DcosClusterCredentials.builder().key(key.get()).dcosUrl(cluster.dcosUrl)
                                   .secretStore(cluster.secretStore).dockerRegistries(dockerRegistries)
                                   .dcosConfig(DcosConfigurationProperties.buildConfig(account, cluster, clusterConfig)).build())
        }

        def dcosCredentials = DcosAccountCredentials.builder().account(account.name).environment(account.environment)
          .accountType(account.accountType).dockerRegistries(account.dockerRegistries)
          .requiredGroupMembership(account.requiredGroupMembership).clusters(clusterCredentials).build()

        // Note: The MapBackedAccountCredentialsRepository doesn't actually use the key for anything currently.
        accountCredentialsRepository.save(dcosCredentials.name, dcosCredentials)
      } catch (e) {
        log.info "Could not load account ${account.name} for DC/OS.", e
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(applicationContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof DcosAccountCredentials
    } as List<DcosAccountCredentials>
  }
}
