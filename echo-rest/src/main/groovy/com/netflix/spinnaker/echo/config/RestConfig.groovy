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

package com.netflix.spinnaker.echo.config

import static retrofit.Endpoints.newFixedEndpoint

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.echo.rest.RestService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

/**
 * Rest endpoint configuration
 */
@Configuration
@ConditionalOnProperty('rest.enabled')
@CompileStatic
@SuppressWarnings('GStringExpressionWithinString')
class RestConfig {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  Client retrofitClient(OkHttpClientConfiguration okHttpClientConfig) {
    new OkClient(okHttpClientConfig.create())
  }

  @Bean
  LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:FULL}') String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel)
  }

  @Bean
  Endpoint restEndpoint(
    @Value('${rest.endpoint}') String restBaseUrl) {
    newFixedEndpoint(restBaseUrl)
  }

  @Bean
  RestService restService(Endpoint restEndpoint, Client retrofitClient, LogLevel retrofitLogLevel) {
    new RestAdapter.Builder()
      .setEndpoint(restEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setConverter(new JacksonConverter())
      .build()
      .create(RestService)
  }

}
