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

package com.netflix.spinnaker.orca.clouddriver.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.FeaturesRestService
import com.netflix.spinnaker.orca.clouddriver.KatoRestService
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ComponentScan([
  "com.netflix.spinnaker.orca.clouddriver",
  "com.netflix.spinnaker.orca.oort.pipeline",
  "com.netflix.spinnaker.orca.oort.tasks",
  "com.netflix.spinnaker.orca.kato.pipeline",
  "com.netflix.spinnaker.orca.kato.tasks"
])
@CompileStatic
class CloudDriverConfiguration {

  @Autowired
  Client retrofitClient

  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @ConditionalOnMissingBean(ObjectMapper)
  @Bean
  ObjectMapper mapper() {
    new OrcaObjectMapper()
  }

  @Bean
  MortService mortDeployService(
    @Value('${mort.baseUrl}') String mortBaseUrl, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(newFixedEndpoint(mortBaseUrl))
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(MortService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(MortService)
  }

  @Bean
  OortService oortDeployService(
    @Value('${oort.baseUrl}') String oortBaseUrl, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(newFixedEndpoint(oortBaseUrl))
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(OortService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(OortService)
  }

  @Bean
  KatoRestService katoDeployService(
    @Value('${kato.baseUrl}') String katoBaseUrl, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(newFixedEndpoint(katoBaseUrl))
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(KatoService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(KatoRestService)
  }

  @Bean
  FeaturesRestService featuresRestService(
    @Value('${kato.baseUrl}') String katoBaseUrl, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(newFixedEndpoint(katoBaseUrl))
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(KatoService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(FeaturesRestService)
  }
}
