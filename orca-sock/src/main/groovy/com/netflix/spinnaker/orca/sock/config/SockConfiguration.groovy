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

package com.netflix.spinnaker.orca.sock.config

import com.google.gson.Gson
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import com.netflix.spinnaker.orca.sock.SockService
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
@CompileStatic
@ComponentScan("com.netflix.spinnaker.orca.sock.tasks")
@ConditionalOnProperty('sock.baseUrl')
class SockConfiguration {

  @Autowired Client retrofitClient
  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Bean Endpoint sockEndpoint(
    @Value('${sock.baseUrl::http://localhost:7010}') String sockBaseUrl) {
    newFixedEndpoint(sockBaseUrl)
  }

  @Bean SockService sockService(Endpoint sockEndpoint, Gson gson) {
    new RestAdapter.Builder()
      .setEndpoint(sockEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(SockService))
      .setConverter(new JacksonConverter())
      .build()
      .create(SockService)
  }

}
