/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.eureka.api.EurekaApiFactory
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.EurekaSupportConfigurationProperties
import com.netflix.spinnaker.clouddriver.eureka.provider.EurekaCachingProvider
import com.netflix.spinnaker.clouddriver.eureka.provider.agent.EurekaAwareProvider
import com.netflix.spinnaker.clouddriver.eureka.provider.agent.EurekaCachingAgent
import com.netflix.spinnaker.clouddriver.eureka.provider.config.EurekaAccountConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

import java.util.regex.Pattern

@Configuration
@EnableConfigurationProperties(EurekaSupportConfigurationProperties)
@ConditionalOnProperty('eureka.provider.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.eureka"])
class EurekaProviderConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("eureka.provider")
  EurekaAccountConfigurationProperties eurekaConfigurationProperties() {
    new EurekaAccountConfigurationProperties()
  }

  @Value('${eureka.pollIntervalMillis:15000}')
  Long pollIntervalMillis

  @Value('${eureka.timeoutMillis:300000}')
  Long timeoutMillis

  @Bean
  EurekaCachingProvider eurekaCachingProvider(EurekaAccountConfigurationProperties eurekaAccountConfigurationProperties,
                                              List<EurekaAwareProvider> eurekaAwareProviderList,
                                              ObjectMapper objectMapper,
                                              EurekaApiFactory eurekaApiFactory) {
    List<EurekaCachingAgent> agents = []
    eurekaAccountConfigurationProperties.accounts.each { EurekaAccountConfigurationProperties.EurekaAccount accountConfig ->
      accountConfig.regions.each { region ->
        String eurekaHost = accountConfig.readOnlyUrl.replaceAll(Pattern.quote('{{region}}'), region)
        boolean multipleEurekaPerAcc = eurekaAccountConfigurationProperties.allowMultipleEurekaPerAccount ?: false
        agents << new EurekaCachingAgent(eurekaApiFactory.createApi(eurekaHost), region, objectMapper, eurekaHost, multipleEurekaPerAcc, accountConfig.name, eurekaAwareProviderList, pollIntervalMillis, timeoutMillis)
      }
    }
    EurekaCachingProvider eurekaCachingProvider = new EurekaCachingProvider(agents)
    eurekaCachingProvider
  }

}
