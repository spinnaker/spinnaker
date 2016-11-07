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

package com.netflix.spinnaker.clouddriver.appengine.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.appengine.config.AppEngineConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Slf4j
@Configuration
class AppEngineCredentialsInitializer implements CredentialsInitializerSynchronizable {
  @Bean
  List<? extends AppEngineNamedAccountCredentials> appEngineNamedAccountCredentials(String clouddriverUserAgentApplicationName,
                                                                                    AppEngineConfigurationProperties appEngineConfigurationProperties,
                                                                                    AccountCredentialsRepository accountCredentialsRepository) {
    synchronizeAppEngineAccounts(clouddriverUserAgentApplicationName,
                                 appEngineConfigurationProperties,
                                 null,
                                 accountCredentialsRepository)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeAppEngineAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<? extends AppEngineNamedAccountCredentials> synchronizeAppEngineAccounts(String clouddriverUserAgentApplicationName,
                                                                                AppEngineConfigurationProperties appEngineConfigurationProperties,
                                                                                CatsModule catsModule,
                                                                                AccountCredentialsRepository accountCredentialsRepository) {
    def (ArrayList<AppEngineConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
      ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                           AppEngineNamedAccountCredentials,
                                           appEngineConfigurationProperties.accounts)

    accountsToAdd.each { AppEngineConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def jsonKey = AppEngineCredentialsInitializer.getJsonKey(managedAccount)
        def appEngineAccount = new AppEngineNamedAccountCredentials.Builder()
          .name(managedAccount.name)
          .environment(managedAccount.environment ?: managedAccount.name)
          .accountType(managedAccount.accountType ?: managedAccount.name)
          .project(managedAccount.project)
          .jsonKey(jsonKey)
          .applicationName(clouddriverUserAgentApplicationName)
          .requiredGroupMembership(managedAccount.requiredGroupMembership)
          .build()

        accountCredentialsRepository.save(managedAccount.name, appEngineAccount)
      } catch (e) {
        log.info("Could not load account $managedAccount.name for App Engine", e)
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    accountCredentialsRepository.all.findAll {
      it instanceof AppEngineNamedAccountCredentials
    } as List<AppEngineNamedAccountCredentials>
  }

  private static String getJsonKey(AppEngineConfigurationProperties.ManagedAccount managedAccount) {
    def inputStream = managedAccount.inputStream

    inputStream ? new String(inputStream.bytes) : null
  }
}
