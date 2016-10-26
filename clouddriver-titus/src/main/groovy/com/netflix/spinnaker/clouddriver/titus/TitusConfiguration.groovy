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

package com.netflix.spinnaker.clouddriver.titus

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusDeployHandler
import com.netflix.spinnaker.clouddriver.titus.client.RegionScopedTitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.health.TitusHealthIndicator
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import java.util.regex.Pattern

@Configuration
@ConditionalOnProperty('titus.enabled')
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.clouddriver.titus')
@Slf4j
class TitusConfiguration {

  @Bean
  @ConfigurationProperties("titus")
  TitusCredentialsConfig titusCredentialsConfig() {
    new TitusCredentialsConfig()
  }

  @Bean
  List<NetflixTitusCredentials> netflixTitusCredentials(TitusCredentialsConfig titusCredentialsConfig,
                                                        AccountCredentialsRepository repository) {
    List<NetflixTitusCredentials> accounts = new ArrayList<>()
    for (TitusCredentialsConfig.Account account in titusCredentialsConfig.accounts) {
      List<TitusRegion> regions = account.regions.collect { new TitusRegion(it.name, account.name, it.endpoint) }
      if (!account.bastionHost && titusCredentialsConfig.defaultBastionHostTemplate) {
        account.bastionHost = titusCredentialsConfig.defaultBastionHostTemplate.replaceAll(Pattern.quote('{{environment}}'), account.environment)
      }
      NetflixTitusCredentials credentials = new NetflixTitusCredentials(account.name, account.environment, account.accountType, regions, account.bastionHost, account.registry, account.awsAccount, titusCredentialsConfig.awsVpc, account.discoveryEnabled, account.discovery)
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    return accounts
  }

  @Bean
  TitusClientProvider titusClientProvider(Registry registry) {
    return new TitusClientProvider(registry)
  }

  @Bean
  TitusDeployHandler titusDeployHandler(TitusClientProvider titusClientProvider) {
    new TitusDeployHandler(titusClientProvider)
  }

  @Bean
  TitusHealthIndicator titusHealthIndicator(AccountCredentialsProvider accountCredentialsProvider, TitusClientProvider titusClientProvider) {
    new TitusHealthIndicator(accountCredentialsProvider, titusClientProvider)
  }

  static class TitusCredentialsConfig {
    String defaultBastionHostTemplate
    String awsVpc
    List<Account> accounts
    static class Account {
      String name
      String environment
      String accountType
      String bastionHost
      Boolean discoveryEnabled
      String discovery
      String awsAccount
      String registry
      List<Region> regions
    }

    static class Region {
      String name
      String endpoint
    }
  }
}
