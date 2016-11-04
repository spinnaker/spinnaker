/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
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
class OpenstackCredentialsInitializer implements CredentialsInitializerSynchronizable {
  private static final Logger LOG = Logger.getLogger(this.class.simpleName)

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  ApplicationContext appContext

  @Autowired
  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers

  @Bean
  List<? extends OpenstackNamedAccountCredentials> openstackNamedAccountCredentials(
    OpenstackConfigurationProperties openstackConfigurationProperties) {
    synchronizeOpenstackAccounts(openstackConfigurationProperties, null)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeOpenstackAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<?> synchronizeOpenstackAccounts(OpenstackConfigurationProperties openstackConfigurationProperties, CatsModule catsModule) {
    def (ArrayList<OpenstackConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
      ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                          OpenstackNamedAccountCredentials,
                                          openstackConfigurationProperties.accounts)

    accountsToAdd.each { OpenstackConfigurationProperties.ManagedAccount managedAccount ->
      LOG.info("Found openstack managed account $managedAccount")
      try {
        def openstackAccount = new OpenstackNamedAccountCredentials(managedAccount.name,
                                                                    managedAccount.environment ?: managedAccount.name,
                                                                    managedAccount.accountType ?: managedAccount.name,
                                                                    managedAccount.username,
                                                                    managedAccount.password,
                                                                    managedAccount.projectName,
                                                                    managedAccount.domainName,
                                                                    managedAccount.authUrl,
                                                                    managedAccount.regions,
                                                                    managedAccount.insecure,
                                                                    managedAccount.heatTemplatePath,
                                                                    managedAccount.lbaas,
                                                                    managedAccount.consul,
                                                                    managedAccount.userDataFile
                                                                    )
        LOG.info("Saving openstack account $openstackAccount")
        accountCredentialsRepository.save(managedAccount.name, openstackAccount)
      } catch (e) {
        LOG.info "Could not load account ${managedAccount.name} for Openstack.", e
      }
    }
    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if ((namesOfDeletedAccounts || accountsToAdd) && catsModule) {
      ProviderUtils.synchronizeAgentProviders(appContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof OpenstackNamedAccountCredentials
    } as List
  }
}
