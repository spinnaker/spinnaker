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
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.config.GoogleConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
class GoogleCredentialsInitializer {

  @Autowired
  GoogleExecutor _googleExecutor  // Not used, just here to force initialization ordering

  @Autowired
  NamerRegistry namerRegistry

  @Bean
  GoogleExecutor initGoogleExecutor() {  // This is to satisfy the autowiring
    return new GoogleExecutor()
  }

  @Bean
  List<? extends GoogleNamedAccountCredentials> googleNamedAccountCredentials(
    String clouddriverUserAgentApplicationName,
    GoogleConfigurationProperties googleConfigurationProperties,
    ApplicationContext applicationContext,
    AccountCredentialsRepository accountCredentialsRepository,
    List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers,
    DeployDefaults googleDeployDefaults) {
    
    synchronizeGoogleAccounts(clouddriverUserAgentApplicationName, googleConfigurationProperties,
      null, applicationContext, accountCredentialsRepository,
      providerSynchronizerTypeWrappers, googleDeployDefaults)
  }

  private List<? extends GoogleNamedAccountCredentials> synchronizeGoogleAccounts(
    String clouddriverUserAgentApplicationName,
    GoogleConfigurationProperties googleConfigurationProperties,
    CatsModule catsModule,
    ApplicationContext applicationContext,
    AccountCredentialsRepository accountCredentialsRepository,
    List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers,
    DeployDefaults googleDeployDefaults) {

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
            .jsonKey(jsonKey)
            .serviceAccountId(managedAccount.serviceAccountId)
            .serviceAccountProject(managedAccount.serviceAccountProject)
            .imageProjects(managedAccount.imageProjects)
            .requiredGroupMembership(managedAccount.requiredGroupMembership)
            .permissions(managedAccount.permissions.build())
            .applicationName(clouddriverUserAgentApplicationName)
            .consulConfig(managedAccount.consul)
            .instanceTypeDisks(googleDeployDefaults.instanceTypeDisks)
            .userDataFile(managedAccount.userDataFile)
            .regionsToManage(managedAccount.regions, googleConfigurationProperties.defaultRegions)
            .namer(namerRegistry.getNamingStrategy(managedAccount.namingStrategy))
            .build()

        if (!managedAccount.project) {
          throw new IllegalArgumentException("No project was specified for Google account $managedAccount.name.");
        }

        accountCredentialsRepository.save(managedAccount.name, googleAccount)
      } catch (e) {
        log.error "Could not load account ${managedAccount.name} for Google.", e
        if (managedAccount.required) {
          throw new IllegalArgumentException("Could not load required account ${managedAccount.name} for Google.", e)
        }
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(applicationContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof GoogleNamedAccountCredentials
    } as List<GoogleNamedAccountCredentials>
  }

  private static String getJsonKey(GoogleConfigurationProperties.ManagedAccount managedAccount) {
    def inputStream = managedAccount.inputStream

    inputStream ? new String(managedAccount.inputStream.bytes) : null
  }
}
