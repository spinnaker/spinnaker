/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.config

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.clouddriver.core.Front50ConfigurationProperties
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@EnableConfigurationProperties(Front50ConfigurationProperties)
class RetrofitConfig {

  @Bean
  @ConditionalOnProperty(name = 'services.front50.enabled', matchIfMissing = true)
  Front50Service front50Service(Front50ConfigurationProperties front50ConfigurationProperties, RestAdapter.LogLevel retrofitLogLevel, OkHttpClientProvider clientProvider, RequestInterceptor spinnakerRequestInterceptor) {
    def endpoint = newFixedEndpoint(front50ConfigurationProperties.baseUrl)
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(endpoint)
      .setClient(new Ok3Client(clientProvider.getClient(new DefaultServiceEndpoint("front50", endpoint.getUrl()))))
      .setConverter(new JacksonConverter())
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(Front50Service))
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .build()
      .create(Front50Service)
  }
}
