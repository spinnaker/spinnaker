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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.eureka.api.EurekaApiFactory
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.EurekaSupportConfigurationProperties
import com.netflix.spinnaker.clouddriver.eureka.provider.EurekaCachingProvider
import com.netflix.spinnaker.clouddriver.eureka.provider.agent.EurekaAwareProvider
import com.netflix.spinnaker.clouddriver.eureka.provider.agent.EurekaCachingAgent
import com.netflix.spinnaker.clouddriver.eureka.provider.config.EurekaAccountConfigurationProperties
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

import java.util.regex.Pattern

@Configuration
@EnableConfigurationProperties(EurekaSupportConfigurationProperties)
@ConditionalOnProperty('eureka.provider.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.eureka"])
class EurekaProviderConfiguration {

  @Autowired
  Registry registry

  @Autowired
  Environment environment

  @Bean
  @ConfigurationProperties("eureka.provider")
  EurekaAccountConfigurationProperties eurekaConfigurationProperties() {
    new EurekaAccountConfigurationProperties()
  }

  private OkHttpClientConfigurationProperties eurekaClientConfig() {
    OkHttpClientConfigurationProperties properties =
      new OkHttpClientConfigurationProperties(
        propagateSpinnakerHeaders: false,
        connectTimoutMs: 10000,
        keyStore: null,
        trustStore: null)
    Binder.get(environment).bind(
      ConfigurationPropertyName.of("eureka.readonly.ok-http-client"),
      Bindable.ofInstance(properties))
    return properties
  }

  private static ObjectMapper getObjectMapper() {
    new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
      .enable(MapperFeature.AUTO_DETECT_CREATORS)
  }

  @Value('${eureka.poll-interval-millis:15000}')
  Long pollIntervalMillis

  @Value('${eureka.timeout-millis:300000}')
  Long timeoutMillis

  @Bean
  EurekaCachingProvider eurekaCachingProvider(EurekaAccountConfigurationProperties eurekaAccountConfigurationProperties,
                                              OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties,
                                              List<EurekaAwareProvider> eurekaAwareProviderList,
                                              ObjectMapper objectMapper,
                                              ServiceClientProvider serviceClientProvider,
                                              SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor) {
    List<EurekaCachingAgent> agents = []
    def eurekaApiFactory = new EurekaApiFactory(serviceClientProvider, getObjectMapper(), spinnakerRequestHeaderInterceptor)
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
