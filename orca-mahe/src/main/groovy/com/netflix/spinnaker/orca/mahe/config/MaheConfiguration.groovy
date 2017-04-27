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

package com.netflix.spinnaker.orca.mahe.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyCleanupListener
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationListener
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
  "com.netflix.spinnaker.orca.mahe.pipeline",
  "com.netflix.spinnaker.orca.mahe.tasks",
  "com.netflix.spinnaker.orca.mahe.cleanup"
])
@ConditionalOnProperty(value = 'mahe.baseUrl')
class MaheConfiguration {

  @Autowired
  Client retrofitClient

  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  ObjectMapper objectMapper

  @Bean
  Endpoint maheEndpoint(@Value('${mahe.baseUrl}') String maheBaseUrl) {
    newFixedEndpoint(maheBaseUrl)
  }

  @Bean
  MaheService maheService(Endpoint maheEndpoint) {
    new RestAdapter.Builder()
      .setEndpoint(maheEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(MaheService))
      .setConverter(new JacksonConverter(objectMapper))
      .build()
      .create(MaheService)
  }

  @Bean
  ApplicationListener<ExecutionEvent> fastPropertyCleanupListenerAdapter(FastPropertyCleanupListener fastPropertyCleanupListener, ExecutionRepository repository) {
    new ExecutionListenerAdapter(fastPropertyCleanupListener, repository)
  }
}
