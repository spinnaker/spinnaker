/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.security

import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean

@CompileStatic
class AzureCredentialsInitializer {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  @Autowired
  String azureApplicationName

  @Bean
  List<AzureNamedAccountCredentials> azureNamedAccountCredentials(AzureConfigurationProperties azureConfigurationProperties,
                                                                  AccountCredentialsRepository accountCredentialsRepository) {

    def azureAccounts = []
    azureConfigurationProperties.accounts.each { AzureConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def azureAccount = new AzureNamedAccountCredentials(
          managedAccount.name,
          managedAccount.environment,
          managedAccount.accountType,
          managedAccount.clientId,
          managedAccount.appKey,
          managedAccount.tenantId,
          managedAccount.subscriptionId,
          managedAccount.regions,
          managedAccount.vmImages,
          managedAccount.customImages,
          managedAccount.defaultResourceGroup,
          managedAccount.defaultKeyVault,
          azureApplicationName
        )

        azureAccounts << (accountCredentialsRepository.save(managedAccount.name, azureAccount) as AzureNamedAccountCredentials)
      } catch (e) {
        log.error("Could not load account ${managedAccount.name} for Azure.", e)
      }
    }

    azureAccounts
  }
}
