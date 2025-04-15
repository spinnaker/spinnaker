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

package com.netflix.spinnaker.orca.igor.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnExpression('${igor.enabled:true}')
@CompileStatic
@ComponentScan("com.netflix.spinnaker.orca.igor")
class IgorConfiguration {

  @Autowired OkHttpClientProvider clientProvider
  @Autowired RestAdapter.LogLevel retrofitLogLevel
  @Autowired ObjectMapper objectMapper

  @Bean
  Endpoint igorEndpoint(
    @Value('${igor.base-url}') String igorBaseUrl) {
    newFixedEndpoint(igorBaseUrl)
  }

  @Bean
  IgorService igorService(Endpoint igorEndpoint, ObjectMapper mapper, RequestInterceptor spinnakerRequestInterceptor) {
    new RestAdapter.Builder()
      .setEndpoint(igorEndpoint)
      .setClient(new Ok3Client(clientProvider.getClient(new DefaultServiceEndpoint("igor", igorEndpoint.url), true)))
      .setLogLevel(retrofitLogLevel)
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setLog(new RetrofitSlf4jLog(IgorService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(IgorService)
  }
}
