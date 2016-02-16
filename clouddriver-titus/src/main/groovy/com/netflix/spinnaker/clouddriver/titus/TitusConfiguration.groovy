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

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusDeployHandler
import com.netflix.spinnaker.clouddriver.titus.client.RegionScopedTitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import javax.annotation.PostConstruct

@Configuration
@ConditionalOnProperty('titus.enabled')
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.clouddriver.titus')
@Slf4j
class TitusConfiguration {

  @PostConstruct
  void init() {
    log.info("TitusConfiguration is enabled")
  }

  @Bean
  @DependsOn('netflixTitusCredentials')
  TitusDeployHandler titusDeployHandler(TitusClientProvider titusClientProvider) {
    new TitusDeployHandler(titusClientProvider)
  }

  @Bean
  @ConfigurationProperties("titus")
  TitusCredentialsConfig titusCredentialsConfig() {
    new TitusCredentialsConfig()
  }

  @Bean(name = "netflixTitusCredentials")
  List<NetflixTitusCredentials> netflixTitusCredentials(TitusCredentialsConfig titusCredentialsConfig,
                                                        AccountCredentialsRepository repository) {
    List<NetflixTitusCredentials> accounts = new ArrayList<>()
    for (TitusCredentialsConfig.Account account in titusCredentialsConfig.accounts) {
      List<TitusRegion> regions = account.regions.collect { new TitusRegion(it.name, account.name, it.endpoint) }
      NetflixTitusCredentials credentials = new NetflixTitusCredentials(account.name, account.environment, account.accountType, regions)
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    return accounts
  }

  @Bean
  @DependsOn("netflixTitusCredentials")
  TitusClientProvider titusClientProvider(@Value('#{netflixTitusCredentials}') List<NetflixTitusCredentials> netflixTitusCredentials) {
    List<TitusClientProvider.TitusClientHolder> titusClientHolders = []
    netflixTitusCredentials.each { credentials ->
      credentials.regions.each { region ->
        titusClientHolders << new TitusClientProvider.TitusClientHolder(
          credentials.name, region.name, new RegionScopedTitusClient(region)
        )
      }
    }
    new TitusClientProvider(titusClientHolders)
  }

  static class TitusCredentialsConfig {
    List<Account> accounts
    static class Account {
      String name
      String environment
      String accountType
      List<Region> regions
    }
    static class Region {
      String name
      String endpoint
    }
  }
}
