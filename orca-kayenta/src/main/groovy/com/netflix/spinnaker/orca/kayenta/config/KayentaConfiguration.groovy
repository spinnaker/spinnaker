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
import retrofit.client.Client
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ComponentScan([
  "com.netflix.spinnaker.orca.kayenta.pipeline",
  "com.netflix.spinnaker.orca.kayenta.tasks"
])
@CompileStatic
@ConditionalOnExpression('${kayenta.enabled:false}')
class KayentaConfiguration {

  @Autowired
  Client retrofitClient

  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @Bean
  Endpoint kayentaEndpoint(
    @Value('${kayenta.baseUrl}') String kayentaBaseUrl) {
    newFixedEndpoint(kayentaBaseUrl)
  }

  @Bean
  KayentaService kayentaService(Endpoint kayentaEndpoint, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(kayentaEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(KayentaService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(KayentaService)
  }
}
