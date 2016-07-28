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

package com.netflix.spinnaker.clouddriver.google.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
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
class GoogleCredentialsInitializer implements CredentialsInitializerSynchronizable {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  @Autowired
  String googleApplicationName

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  ApplicationContext appContext

  @Autowired
  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers

  @Bean
  List<? extends GoogleNamedAccountCredentials> googleNamedAccountCredentials(
    GoogleConfigurationProperties googleConfigurationProperties) {
    synchronizeGoogleAccounts(googleConfigurationProperties, null)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeGoogleAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<?> synchronizeGoogleAccounts(GoogleConfigurationProperties googleConfigurationProperties, CatsModule catsModule) {
    def (ArrayList<GoogleConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
      ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                           GoogleNamedAccountCredentials,
                                           googleConfigurationProperties.accounts)

    accountsToAdd.each { GoogleConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def jsonKey = GoogleCredentialsInitializer.getJsonKey(managedAccount)
        def googleAccount = new GoogleNamedAccountCredentials.Builder()
            .name(managedAccount.name)
            .environment(managedAccount.environment ?: managedAccount.name)
            .accountType(managedAccount.accountType ?: managedAccount.name)
            .project(managedAccount.project)
            .computeVersion(managedAccount.alphaListed ? ComputeVersion.ALPHA : ComputeVersion.DEFAULT)
            .httpLoadBalancingEnabled(managedAccount.httpLoadBalancingEnabled)
            .jsonKey(jsonKey)
            .imageProjects(managedAccount.imageProjects)
            .requiredGroupMembership(managedAccount.requiredGroupMembership)
            .applicationName(googleApplicationName)
            .consulConfig(managedAccount.consul)
            .build()

        if (!managedAccount.project) {
          throw new IllegalArgumentException("No project was specified for Google account $managedAccount.name.");
        }

        accountCredentialsRepository.save(managedAccount.name, googleAccount)
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for Google.", e
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(appContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof GoogleNamedAccountCredentials
    } as List
  }

  private static String getJsonKey(GoogleConfigurationProperties.ManagedAccount managedAccount) {
    def inputStream = managedAccount.inputStream

    inputStream ? new String(managedAccount.inputStream.bytes) : null
  }
}
