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

package com.netflix.spinnaker.oort.titan.credentials.config

import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.oort.titan.credentials.NetflixTitanCredentials
import com.netflix.titanclient.model.TitanRegion
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author sthadeshwar
 */
@Configuration
@EnableConfigurationProperties
class TitanCredentialsConfig {

  @Bean
  @ConfigurationProperties("titan")
  CredentialsConfig titanConfig() {
    new CredentialsConfig()
  }

  @Bean
  List<? extends NetflixTitanCredentials> netflixTitanCredentials(CredentialsConfig credentialsConfig,
                                                                  AccountCredentialsRepository accountCredentialsRepository,
                                                                  @Value('${default.account.env:test}') String defaultEnv) {
    if (!credentialsConfig.accounts) {
      credentialsConfig.accounts = [new CredentialsConfig.Account(name: defaultEnv)]
      if (!credentialsConfig.defaultRegions) {
        credentialsConfig.defaultRegions = [TitanRegion.US_EAST_1, TitanRegion.US_WEST_2, TitanRegion.EU_WEST_1].collect { new NetflixTitanCredentials.Region(name: it.name) }
      }
    }
    List<? extends NetflixTitanCredentials> accounts = []
    for (CredentialsConfig.Account account in credentialsConfig.accounts) {
      def regionsList = account.regions ?: credentialsConfig.defaultRegions
      List<NetflixTitanCredentials.Region> regions = regionsList.collect { new NetflixTitanCredentials.Region(name: it.name) }
      def credentials = new NetflixTitanCredentials(account.name, regions)
      accounts.add(credentials)
      accountCredentialsRepository.save(account.name, credentials)
    }
    accounts
  }

}
