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

import com.netflix.spinnaker.echo.mayo.MayoService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient

@Configuration
@Slf4j
@CompileStatic
class MayoConfig {

  @Bean
  Client retrofitClient() {
    new OkClient()
  }

  @Bean
  LogLevel retrofitLogLevel() {
    LogLevel.NONE
  }

  @Bean
  Endpoint mayoEndpoint(@Value('${mayo.baseUrl}') String mayoBaseUrl) {
    newFixedEndpoint(mayoBaseUrl)
  }

  @Bean
  MayoService mayoService(Endpoint mayoEndpoint, Client retrofitClient, LogLevel retrofitLogLevel) {
    log.info('mayo service loaded')
    new RestAdapter.Builder()
      .setEndpoint(mayoEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .build()
      .create(MayoService)
  }

}
