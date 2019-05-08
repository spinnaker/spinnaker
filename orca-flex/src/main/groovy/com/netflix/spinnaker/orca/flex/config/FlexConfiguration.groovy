/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.flex.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.flex.FlexService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = "flex.base-url")
@ComponentScan([
  "com.netflix.spinnaker.orca.flex.pipeline",
  "com.netflix.spinnaker.orca.flex.tasks"
])
@CompileStatic
class FlexConfiguration {

  @Autowired Client retrofitClient
  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Bean
  Endpoint flexEndpoint(
    @Value('${flex.base-url}') String flexBaseUrl) {
    newFixedEndpoint(flexBaseUrl)
  }

  @Bean
  FlexService flexService(Endpoint flexEndpoint, ObjectMapper mapper) {
    new RestAdapter.Builder()
      .setEndpoint(flexEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(FlexService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(FlexService)
  }
}
