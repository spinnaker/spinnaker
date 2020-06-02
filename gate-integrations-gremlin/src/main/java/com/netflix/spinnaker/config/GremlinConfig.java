/*
 * Copyright 2019 Gremlin, Inc.
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

package com.netflix.spinnaker.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.gate.config.Service;
import com.netflix.spinnaker.gate.config.ServiceConfiguration;
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger;
import com.netflix.spinnaker.gate.services.EurekaLookupService;
import com.netflix.spinnaker.gate.services.gremlin.GremlinService;
import groovy.transform.CompileStatic;
import groovy.util.logging.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@Slf4j
@CompileStatic
@Configuration
@ConditionalOnProperty("integrations.gremlin.enabled")
class GremlinConfig {
  @Bean
  GremlinService gremlinService(
      OkHttpClient okHttpClient,
      ServiceConfiguration serviceConfiguration,
      Registry registry,
      EurekaLookupService eurekaLookupService,
      RequestInterceptor spinnakerRequestInterceptor,
      @Value("${retrofit.log-level:BASIC}") String retrofitLogLevel) {
    return createClient(
        "gremlin",
        GremlinService.class,
        okHttpClient,
        serviceConfiguration,
        registry,
        eurekaLookupService,
        spinnakerRequestInterceptor,
        retrofitLogLevel);
  }

  private <T> T createClient(
      String serviceName,
      Class<T> type,
      OkHttpClient okHttpClient,
      ServiceConfiguration serviceConfiguration,
      Registry registry,
      EurekaLookupService eurekaLookupService,
      RequestInterceptor spinnakerRequestInterceptor,
      String retrofitLogLevel) {
    Service service = serviceConfiguration.getService(serviceName);
    if (service == null) {
      throw new IllegalArgumentException(
          "Unknown service ${serviceName} requested of type ${type}");
    }

    if (!service.isEnabled()) {
      return null;
    }

    Endpoint endpoint = newFixedEndpoint(service.getBaseUrl());
    return buildService(
        okHttpClient, type, endpoint, spinnakerRequestInterceptor, retrofitLogLevel);
  }

  private <T> T buildService(
      OkHttpClient client,
      Class<T> type,
      Endpoint endpoint,
      RequestInterceptor spinnakerRequestInterceptor,
      String retrofitLogLevel) {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    return new RestAdapter.Builder()
        .setRequestInterceptor(spinnakerRequestInterceptor)
        .setEndpoint(endpoint)
        .setClient(new Ok3Client(client))
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
        .setLog(new Slf4jRetrofitLogger(type))
        .build()
        .create(type);
  }
}
