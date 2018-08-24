/*
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.core.Front50ConfigurationProperties
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@EnableConfigurationProperties(Front50ConfigurationProperties)
class RetrofitConfig {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkClient okClient(OkHttpClientConfiguration okHttpClientConfiguration) {
    def client = okHttpClientConfiguration.create()
    return new OkClient(client)
  }

  @Bean
  @ConditionalOnProperty(name = 'services.front50.enabled', matchIfMissing = true)
  Front50Service front50Service(Front50ConfigurationProperties front50ConfigurationProperties, RestAdapter.LogLevel retrofitLogLevel, OkClient okClient, RequestInterceptor spinnakerRequestInterceptor) {
    def endpoint = newFixedEndpoint(front50ConfigurationProperties.baseUrl)
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(endpoint)
      .setClient(okClient)
      .setConverter(new JacksonConverter())
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(Front50Service))
      .build()
      .create(Front50Service)
  }
}
