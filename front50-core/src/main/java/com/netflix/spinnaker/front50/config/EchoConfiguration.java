/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.config;

import static retrofit.RestAdapter.LogLevel.BASIC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.front50.echo.EchoService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

/** echo service configuration */
@Configuration
public class EchoConfiguration {
  @Bean
  EchoService echoService(
      OkHttpClientProvider okHttpClientProvider, @Value("${echo.base-url:none}") String baseUrl) {
    if ("none".equals(baseUrl)) {
      return null;
    }

    OkHttpClient okHttpClient =
        okHttpClientProvider.getClient(new DefaultServiceEndpoint("echo", baseUrl));
    ObjectMapper objectMapper = new ObjectMapper();
    JacksonConverter jacksonConverter = new JacksonConverter(objectMapper);

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(baseUrl))
        .setClient(new Ok3Client(okHttpClient))
        .setConverter(jacksonConverter)
        .setLogLevel(BASIC)
        .setLog(new Slf4jRetrofitLogger(EchoService.class))
        .build()
        .create(EchoService.class);
  }
}
