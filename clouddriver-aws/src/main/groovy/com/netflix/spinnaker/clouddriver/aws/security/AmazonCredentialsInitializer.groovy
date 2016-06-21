/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

import static com.amazonaws.regions.Regions.*

@Configuration
@EnableConfigurationProperties
class AmazonCredentialsInitializer implements CredentialsInitializerSynchronizable  {
  @Autowired
  ApplicationContext appContext;

  @Autowired
  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties('aws')
  CredentialsConfig credentialsConfig() {
    new CredentialsConfig()
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  Class<? extends NetflixAmazonCredentials> credentialsType(CredentialsConfig credentialsConfig) {
    if (!credentialsConfig.accounts && !credentialsConfig.defaultAssumeRole) {
      NetflixAmazonCredentials
    } else {
      NetflixAssumeRoleAmazonCredentials
    }
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader(AWSCredentialsProvider awsCredentialsProvider, AmazonClientProvider amazonClientProvider, Class<? extends NetflixAmazonCredentials> credentialsType) {
    new CredentialsLoader<? extends NetflixAmazonCredentials>(awsCredentialsProvider, amazonClientProvider, credentialsType)
  }

  @Bean
  List<? extends NetflixAmazonCredentials> netflixAmazonCredentials(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                    CredentialsConfig credentialsConfig,
                                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                                    @Value('${default.account.env:default}') String defaultAccountName,
                                                                    @Value('${default.account.environment:#{default.account.env}}') String defaultEnvironment,
                                                                    @Value('${default.account.accountType:#{default.account.env}}') String defaultAccountType) {
    synchronizeAmazonAccounts(credentialsLoader, credentialsConfig, accountCredentialsRepository, defaultAccountName, defaultEnvironment, defaultAccountType, null)
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<? extends NetflixAmazonCredentials> synchronizeAmazonAccounts(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                    CredentialsConfig credentialsConfig,
                                    AccountCredentialsRepository accountCredentialsRepository,
                                    @Value('${default.account.env:default}') String defaultAccountName,
                                    @Value('${default.account.environment:#{default.account.env}}') String defaultEnvironment,
                                    @Value('${default.account.accountType:#{default.account.env}}') String defaultAccountType,
                                    CatsModule catsModule) {
    if (!credentialsConfig.accounts && !credentialsConfig.defaultAssumeRole) {
      credentialsConfig.accounts = [new CredentialsConfig.Account(name: defaultAccountName, environment: defaultEnvironment, accountType: defaultAccountType)]
      if (!credentialsConfig.defaultRegions) {
        credentialsConfig.defaultRegions = [US_EAST_1, US_WEST_1, US_WEST_2, EU_WEST_1].collect { new CredentialsConfig.Region(name: it.name) }
      }
    }

    List<? extends NetflixAmazonCredentials> accounts = credentialsLoader.load(credentialsConfig)

    def (ArrayList<NetflixAmazonCredentials> accountsToAdd, List<String> namesOfDeletedAccounts) =
      ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, NetflixAmazonCredentials, accounts)

    accountsToAdd.each { NetflixAmazonCredentials account ->
      accountCredentialsRepository.save(account.name, account)
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if ((namesOfDeletedAccounts || accountsToAdd) && catsModule) {
      ProviderUtils.synchronizeAgentProviders(appContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof NetflixAmazonCredentials
    } as List
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeAmazonAccounts"
  }
}
