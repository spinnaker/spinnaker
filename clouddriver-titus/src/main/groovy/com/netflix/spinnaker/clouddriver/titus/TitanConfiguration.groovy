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
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitanDeployHandler
import com.netflix.titanclient.RegionScopedTitanClient
import com.netflix.titanclient.TitanRegion
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
@ConditionalOnProperty('titan.enabled')
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.clouddriver.titus')
@Slf4j
class TitanConfiguration {

  @PostConstruct
  void init() {
    log.info("TitanConfiguration is enabled")
  }

  @Bean
  @DependsOn('netflixTitanCredentials')
  TitanDeployHandler titanDeployHandler(TitanClientProvider titanClientProvider) {
    new TitanDeployHandler(titanClientProvider)
  }  

  @Bean
  @ConfigurationProperties("titan")
  TitanCredentialsConfig titanCredentialsConfig() {
    new TitanCredentialsConfig()
  }

  @Bean(name = "netflixTitanCredentials")
  List<NetflixTitanCredentials> netflixTitanCredentials(TitanCredentialsConfig titanCredentialsConfig,
                                                        AccountCredentialsRepository repository) {
    List<NetflixTitanCredentials> accounts = new ArrayList<>()
    for (TitanCredentialsConfig.Account account in titanCredentialsConfig.accounts) {
      List<TitanRegion> regions = account.regions.collect { new TitanRegion(it.name, account.name, it.endpoint, it.calypsoEndpoint) }
      NetflixTitanCredentials credentials = new NetflixTitanCredentials(account.name, account.environment, account.accountType, regions)
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    return accounts
  }

  @Bean
  @DependsOn("netflixTitanCredentials")
  TitanClientProvider titanClientProvider(@Value('#{netflixTitanCredentials}') List<NetflixTitanCredentials> netflixTitanCredentials) {
    List<TitanClientProvider.TitanClientHolder> titanClientHolders = []
    netflixTitanCredentials.each { credentials ->
      credentials.regions.each { region ->
        titanClientHolders << new TitanClientProvider.TitanClientHolder(
          credentials.name, region.name, new RegionScopedTitanClient(region, true)
        )
      }
    }
    new TitanClientProvider(titanClientHolders)
  }

  static class TitanCredentialsConfig {
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
      String calypsoEndpoint
    }
  }
}
