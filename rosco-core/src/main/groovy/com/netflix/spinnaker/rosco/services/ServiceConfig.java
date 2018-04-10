/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.rosco.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Configuration
public class ServiceConfig {
  @Value("${services.clouddriver.baseUrl:http://localhost:7002}")
  String clouddriverBaseUrl;

  @Value("${retrofit.logLevel:BASIC}")
  String retrofitLogLevel;

  // This should be service-agnostic if more integrations than clouddriver are used
  @Bean
  ClouddriverService clouddriverService() {
    ObjectMapper objectMapper = new ObjectMapper()
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    return new RestAdapter.Builder()
        .setEndpoint(clouddriverBaseUrl)
        .setClient(new OkClient())
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
        .build()
        .create(ClouddriverService.class);
  }
}
