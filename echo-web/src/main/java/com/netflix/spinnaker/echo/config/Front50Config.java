/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.netflix.spinnaker.echo.services.Front50Service;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter.Builder;
import retrofit.RestAdapter.LogLevel;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Configuration
@Slf4j
public class Front50Config {
  @Value("${okHttpClient.connectionPool.maxIdleConnections:5}")
  private int maxIdleConnections;

  @Value("${okHttpClient.connectionPool.keepAliveDurationMs:300000}")
  private int keepAliveDurationMs;

  @Value("${okHttpClient.retryOnConnectionFailure:true}")
  private boolean retryOnConnectionFailure;

  @Bean
  public OkHttpClient okHttpClient(OkHttpClientConfiguration okHttpClientConfig) {
    OkHttpClient cli = okHttpClientConfig.create();
    cli.setConnectionPool(new ConnectionPool(maxIdleConnections, keepAliveDurationMs));
    cli.setRetryOnConnectionFailure(retryOnConnectionFailure);
    return cli;
  }

  @Bean
  public LogLevel retrofitLogLevel() {
    return LogLevel.BASIC;
  }

  @Bean
  public Endpoint front50Endpoint(@Value("${front50.baseUrl}") String front50BaseUrl) {
    return Endpoints.newFixedEndpoint(front50BaseUrl);
  }

  @Bean
  public Front50Service front50Service(Endpoint front50Endpoint, OkHttpClient okHttpClient,
    LogLevel retrofitLogLevel) {
    log.info("front50 service loaded");

    return new Builder()
      .setEndpoint(front50Endpoint)
      .setConverter(new JacksonConverter())
      .setClient(new OkClient(okHttpClient))
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(Front50Service.class)).build()
      .create(Front50Service.class);
  }
}
