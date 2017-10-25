/*
 * Copyright 2017 Netflix, Inc.
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

import java.util.List;
import com.netflix.spinnaker.echo.notification.DryRunNotificationAgent;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import static retrofit.Endpoints.newFixedEndpoint;

@Configuration
@EnableConfigurationProperties(DryRunConfig.DryRunProperties.class)
@ConditionalOnProperty("dryrun.enabled")
@Slf4j
public class DryRunConfig {

  @Bean
  Endpoint dryRunEndpoint(DryRunProperties properties) {
    return newFixedEndpoint(properties.getBaseUrl());
  }

  @Bean DryRunNotificationAgent dryRunNotificationAgent(
    Front50Service front50,
    OkHttpClient okHttpClient,
    RestAdapter.LogLevel retrofitLogLevel,
    Endpoint dryRunEndpoint,
    DryRunProperties properties)
  {
    log.info("Pipeline dry runs will execute at {}", dryRunEndpoint.getUrl());
    OrcaService orca = new RestAdapter.Builder()
      .setEndpoint(dryRunEndpoint)
      .setClient(new OkClient(okHttpClient))
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(OrcaService.class))
      .build()
      .create(OrcaService.class);
    return new DryRunNotificationAgent(front50, orca, properties);
  }

  @ConfigurationProperties("dryrun")
  @Data
  public static class DryRunProperties {
    String baseUrl;
    List<Notification> notifications;
  }

  // seems like I have to do this as Spring can't parse lists of strings from YAML
  @Data
  public static class Notification {
    String type;
    String address;
    String level;
    List<String> when;
  }
}
