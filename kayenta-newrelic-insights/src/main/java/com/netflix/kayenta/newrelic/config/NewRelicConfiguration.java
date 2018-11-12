/*
 * Copyright 2018 Adobe
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

package com.netflix.kayenta.newrelic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.newrelic.metrics.NewRelicMetricsService;
import com.netflix.kayenta.newrelic.security.NewRelicCredentials;
import com.netflix.kayenta.newrelic.security.NewRelicNamedAccountCredentials;
import com.netflix.kayenta.newrelic.service.NewRelicRemoteService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import retrofit.converter.JacksonConverter;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.newrelic.enabled")
@ComponentScan({"com.netflix.kayenta.newrelic"})
@Slf4j
public class NewRelicConfiguration {

  private static final String NEWRELIC_INSIGHTS_ENDPOINT = "https://insights-api.newrelic.com";

  @Bean
  @ConfigurationProperties("kayenta.newrelic")
  NewRelicConfigurationProperties newrelicConfigurationProperties() {
    return new NewRelicConfigurationProperties();
  }

  @Bean
  @ConfigurationProperties("kayenta.newrelic.testControllerDefaults")
  NewRelicConfigurationTestControllerDefaultProperties newrelicConfigurationTestControllerDefaultProperties() {
    return new NewRelicConfigurationTestControllerDefaultProperties();
  }

  @Bean
  MetricsService newrelicMetricsService(
    NewRelicConfigurationProperties newrelicConfigurationProperties,
    RetrofitClientFactory retrofitClientFactory,
    ObjectMapper objectMapper,
    OkHttpClient okHttpClient,
    AccountCredentialsRepository accountCredentialsRepository) throws IOException {
    NewRelicMetricsService.NewRelicMetricsServiceBuilder metricsServiceBuilder =
      NewRelicMetricsService.builder();

    for (NewRelicManagedAccount account : newrelicConfigurationProperties.getAccounts()) {
      String name = account.getName();
      List<AccountCredentials.Type> supportedTypes = account.getSupportedTypes();

      NewRelicCredentials credentials = NewRelicCredentials.builder()
        .apiKey(account.getApiKey())
        .applicationKey(account.getApplicationKey())
        .build();

      RemoteService remoteService = new RemoteService().setBaseUrl(NEWRELIC_INSIGHTS_ENDPOINT);

      NewRelicNamedAccountCredentials.NewRelicNamedAccountCredentialsBuilder accountCredentialsBuilder =
        NewRelicNamedAccountCredentials.builder()
          .name(name)
          .endpoint(remoteService)
          .credentials(credentials);

      if (!CollectionUtils.isEmpty(supportedTypes)) {
        if (supportedTypes.contains(AccountCredentials.Type.METRICS_STORE)) {
          accountCredentialsBuilder.newRelicRemoteService(retrofitClientFactory.createClient(
            NewRelicRemoteService.class,
            new JacksonConverter(objectMapper),
            remoteService,
            okHttpClient
          ));
        }
        accountCredentialsBuilder.supportedTypes(supportedTypes);
      }

      accountCredentialsRepository.save(name, accountCredentialsBuilder.build());
      metricsServiceBuilder.accountName(name);
    }

    log.info("Populated NewRelicMetricsService with {} NewRelic accounts.",
      newrelicConfigurationProperties.getAccounts().size());
    return metricsServiceBuilder.build();
  }
}

