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

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import com.squareup.okhttp.ConnectionPool
import com.squareup.okhttp.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired

import static retrofit.Endpoints.newFixedEndpoint

import com.netflix.spinnaker.echo.services.Front50Service
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.OkClient

@Configuration
@Slf4j
@CompileStatic
class Front50Config {
  @Autowired
  OkHttpClientConfiguration okHttpClientConfig

  @Value('${okHttpClient.connectionPool.maxIdleConnections:5}')
  int maxIdleConnections

  @Value('${okHttpClient.connectionPool.keepAliveDurationMs:300000}')
  int keepAliveDurationMs

  @Value('${okHttpClient.retryOnConnectionFailure:true}')
  boolean retryOnConnectionFailure

  @Bean
  OkHttpClient okHttpClient() {
    def cli = okHttpClientConfig.create()
    cli.connectionPool = new ConnectionPool(maxIdleConnections, keepAliveDurationMs)
    cli.retryOnConnectionFailure = retryOnConnectionFailure
    return cli
  }

  @Bean
  LogLevel retrofitLogLevel() {
    LogLevel.BASIC
  }

  @Bean
  Endpoint front50Endpoint(@Value('${front50.baseUrl}') String front50BaseUrl) {
    newFixedEndpoint(front50BaseUrl)
  }

  @Bean
  Front50Service front50Service(Endpoint front50Endpoint, OkHttpClient okHttpClient, LogLevel retrofitLogLevel) {
    log.info('front50 service loaded')
    new RestAdapter.Builder()
      .setEndpoint(front50Endpoint)
      .setClient(new OkClient(okHttpClient))
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(Front50Service.class))
      .build()
      .create(Front50Service.class)
  }

}
