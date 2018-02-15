/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.Endpoints.newFixedEndpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter

@Configuration
@Import(RetrofitConfiguration::class)
@ComponentScan(
  "com.netflix.spinnaker.orca.kayenta.pipeline",
  "com.netflix.spinnaker.orca.kayenta.tasks"
)
@ConditionalOnExpression("\${kayenta.enabled:false}")
class KayentaConfiguration {

  @Autowired
  private lateinit var retrofitClient: Client

  @Autowired
  private lateinit var retrofitLogLevel: RestAdapter.LogLevel

  @Autowired
  private lateinit var spinnakerRequestInterceptor: RequestInterceptor

  @Bean
  fun kayentaEndpoint(
    @Value("\${kayenta.baseUrl}") kayentaBaseUrl: String): Endpoint {
    return newFixedEndpoint(kayentaBaseUrl)
  }

  @Bean
  internal fun kayentaService(kayentaEndpoint: Endpoint, mapper: ObjectMapper): KayentaService {
    return RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(kayentaEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(RetrofitSlf4jLog(KayentaService::class.java))
      .setConverter(JacksonConverter(mapper))
      .build()
      .create(KayentaService::class.java)
  }
}
