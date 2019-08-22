/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.echo.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.netflix.spinnaker.echo.telemetry.TelemetryService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import groovy.transform.CompileStatic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.JacksonConverter;

@Slf4j
@Configuration
@ConditionalOnProperty("telemetry.enabled")
@CompileStatic
class TelemetryConfig {

  @Value("${telemetry.endpoint}")
  String endpoint;

  @Bean
  Endpoint telemetryEndpoint() {
    return newFixedEndpoint(endpoint);
  }

  @Bean
  public TelemetryService telemetryService(
      Endpoint telemetryEndpoint, Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {
    log.info("Telemetry service loaded");

    TelemetryService client =
        new RestAdapter.Builder()
            .setEndpoint(telemetryEndpoint)
            .setConverter(new JacksonConverter())
            .setClient(retrofitClient)
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .setLog(new Slf4jRetrofitLogger(TelemetryService.class))
            .build()
            .create(TelemetryService.class);

    return client;
  }
}
