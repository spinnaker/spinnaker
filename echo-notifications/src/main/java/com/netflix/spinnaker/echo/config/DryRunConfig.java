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

import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.echo.notification.DryRunNotificationAgent;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import com.netflix.spinnaker.echo.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@EnableConfigurationProperties(DryRunConfig.DryRunProperties.class)
@ConditionalOnProperty("dryrun.enabled")
@Slf4j
public class DryRunConfig {

  @Bean
  DryRunNotificationAgent dryRunNotificationAgent(
      Front50Service front50, OkHttpClientProvider clientProvider, DryRunProperties properties) {
    log.info("Pipeline dry runs will execute at {}", properties.getBaseUrl());
    OrcaService orca =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(properties.getBaseUrl()))
            .client(
                clientProvider.getClient(
                    new DefaultServiceEndpoint("orca", properties.getBaseUrl())))
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
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
