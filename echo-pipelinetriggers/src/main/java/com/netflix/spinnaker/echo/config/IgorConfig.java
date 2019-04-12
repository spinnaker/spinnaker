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

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter.Builder;
import retrofit.RestAdapter.LogLevel;
import retrofit.converter.JacksonConverter;

@Configuration
@ConditionalOnProperty("igor.enabled")
@Slf4j
public class IgorConfig {
  @Bean
  public Endpoint igorEndpoint(@Value("${igor.baseUrl}") String igorBaseUrl) {
    return Endpoints.newFixedEndpoint(igorBaseUrl);
  }

  @Bean
  public IgorService igorService(Endpoint igorEndpoint, Ok3Client ok3Client,
                                 LogLevel retrofitLogLevel, RequestInterceptor spinnakerRequestInterceptor) {
    log.info("igor service loaded");
    return new Builder()
      .setEndpoint(igorEndpoint)
      .setConverter(new JacksonConverter())
      .setClient(ok3Client)
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(IgorService.class)).build()
      .create(IgorService.class);
  }
}
