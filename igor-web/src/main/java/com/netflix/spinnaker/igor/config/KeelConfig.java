/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.igor.config;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@ConditionalOnProperty("services.keel.enabled")
@Configuration
public class KeelConfig {

  @Bean
  public RestAdapter.LogLevel retrofitLogLevel(
      @Value("${retrofit.log-level:BASIC}") String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel);
  }

  @Bean
  public Endpoint keelEndpoint(@Value("${services.keel.base-url}") String keelBaseUrl) {
    return Endpoints.newFixedEndpoint(keelBaseUrl);
  }

  @Bean
  public KeelService keelService(
      Endpoint keelEndpoint, Ok3Client ok3Client, RestAdapter.LogLevel retrofitLogLevel) {
    return new RestAdapter.Builder()
        .setEndpoint(keelEndpoint)
        .setConverter(new JacksonConverter())
        .setClient(ok3Client)
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(KeelService.class))
        .build()
        .create(KeelService.class);
  }
}
