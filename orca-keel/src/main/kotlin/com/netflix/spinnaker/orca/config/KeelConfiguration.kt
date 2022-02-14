/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter

@Configuration
@ConditionalOnProperty("services.keel.enabled")
@ComponentScan(
  basePackages = [
    "com.netflix.spinnaker.orca.keel.task",
    "com.netflix.spinnaker.orca.keel.pipeline"
  ]
)
class KeelConfiguration {
  @Bean fun keelEndpoint(@Value("\${services.keel.base-url}") keelBaseUrl: String): Endpoint {
    return Endpoints.newFixedEndpoint(keelBaseUrl)
  }

  @Bean fun keelService(
    keelEndpoint: Endpoint,
    keelObjectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider,
    retrofitLogLevel: RestAdapter.LogLevel,
    spinnakerRequestInterceptor: RequestInterceptor
  ) =
    RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(keelEndpoint)
      .setClient(Ok3Client(clientProvider.getClient(DefaultServiceEndpoint("keel", keelEndpoint.url))))
      .setLogLevel(retrofitLogLevel)
      .setConverter(JacksonConverter(keelObjectMapper))
      .build()
      .create(KeelService::class.java)

  @Bean fun keelObjectMapper() =
    OrcaObjectMapper.newInstance()
      .registerModule(KotlinModule.Builder().build())
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
