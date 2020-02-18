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

import com.netflix.spinnaker.echo.telemetry.TelemetryService;
import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import de.huxhorn.sulky.ulid.ULID;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Slf4j
@Configuration
@ConditionalOnProperty("telemetry.enabled")
@EnableConfigurationProperties(TelemetryConfig.TelemetryConfigProps.class)
public class TelemetryConfig {

  @Bean
  public TelemetryService telemetryService(
      RetrofitConfigurationProperties retrofitConfigurationProperties,
      TelemetryConfigProps configProps) {
    log.info("Telemetry service loaded");

    TelemetryService client =
        new RestAdapter.Builder()
            .setEndpoint(configProps.endpoint)
            .setConverter(new JacksonConverter())
            .setClient(telemetryOkClient(configProps))
            .setLogLevel(retrofitConfigurationProperties.getLogLevel())
            .setLog(new Slf4jRetrofitLogger(TelemetryService.class))
            .build()
            .create(TelemetryService.class);

    return client;
  }

  private OkClient telemetryOkClient(TelemetryConfigProps configProps) {
    OkHttpClient httpClient = new OkHttpClient();
    httpClient.setConnectTimeout(configProps.connectionTimeoutMillis, TimeUnit.MILLISECONDS);
    httpClient.setReadTimeout(configProps.readTimeoutMillis, TimeUnit.MILLISECONDS);
    return new OkClient(httpClient);
  }

  @Data
  @ConfigurationProperties(prefix = "telemetry")
  public static class TelemetryConfigProps {

    public static final String DEFAULT_TELEMETRY_ENDPOINT = "https://stats.spinnaker.io/log";

    boolean enabled = false;
    String endpoint = DEFAULT_TELEMETRY_ENDPOINT;
    String instanceId = new ULID().nextULID();
    String spinnakerVersion = "unknown";
    DeploymentMethod deploymentMethod = new DeploymentMethod();
    int connectionTimeoutMillis = 3000;
    int readTimeoutMillis = 5000;

    @Data
    public static class DeploymentMethod {
      private String type;
      private String version;

      public Optional<String> getType() {
        return Optional.ofNullable(type);
      }

      public Optional<String> getVersion() {
        return Optional.ofNullable(version);
      }
    }
  }
}
