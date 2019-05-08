/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
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
@Import([RetrofitConfiguration])
@ComponentScan([
  "com.netflix.spinnaker.orca.mine.pipeline",
  "com.netflix.spinnaker.orca.mine.tasks"
])
@ConditionalOnProperty(value = "mine.base-url")
class MineConfiguration {

  @Autowired
  Client retrofitClient
  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  ObjectMapper objectMapper

  @Bean
  Endpoint mineEndpoint(
    @Value('${mine.base-url}') String mineBaseUrl) {
    newFixedEndpoint(mineBaseUrl)
  }

  @Bean
  MineService mineService(Endpoint mineEndpoint) {
    new RestAdapter.Builder()
      .setEndpoint(mineEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(MineService))
      .setConverter(new JacksonConverter(objectMapper))
      .build()
      .create(MineService)
  }
}
