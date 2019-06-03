/*
 * Copyright 2018 Armory, Inc.
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

package com.netflix.kayenta.datadog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.datadog.metrics.DatadogMetricsService;
import com.netflix.kayenta.datadog.security.DatadogCredentials;
import com.netflix.kayenta.datadog.security.DatadogNamedAccountCredentials;
import com.netflix.kayenta.datadog.service.DatadogRemoteService;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import retrofit.converter.JacksonConverter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.datadog.enabled")
@ComponentScan({"com.netflix.kayenta.datadog"})
@Slf4j
public class DatadogConfiguration {
  @Bean
  @ConfigurationProperties("kayenta.datadog")
  DatadogConfigurationProperties datadogConfigurationProperties() {
    return new DatadogConfigurationProperties();
  }

  @Bean
  @ConfigurationProperties("kayenta.datadog.test-controller-defaults")
  DatadogConfigurationTestControllerDefaultProperties datadogConfigurationTestControllerDefaultProperties() {
    return new DatadogConfigurationTestControllerDefaultProperties();
  }

  @Bean
  MetricsService datadogMetricsService(DatadogConfigurationProperties datadogConfigurationProperties, RetrofitClientFactory retrofitClientFactory, ObjectMapper objectMapper, OkHttpClient okHttpClient, AccountCredentialsRepository accountCredentialsRepository) throws IOException {
    DatadogMetricsService.DatadogMetricsServiceBuilder metricsServiceBuilder = DatadogMetricsService.builder();

    for (DatadogManagedAccount account : datadogConfigurationProperties.getAccounts()) {
      String name = account.getName();
      List<AccountCredentials.Type> supportedTypes = account.getSupportedTypes();

      DatadogCredentials credentials = DatadogCredentials
        .builder()
        .apiKey(account.getApiKey())
        .applicationKey(account.getApplicationKey())
        .build();

      DatadogNamedAccountCredentials.DatadogNamedAccountCredentialsBuilder accountCredentialsBuilder =
        DatadogNamedAccountCredentials
          .builder()
          .name(name)
          .endpoint(account.getEndpoint())
          .credentials(credentials);

      if (!CollectionUtils.isEmpty(supportedTypes)) {
        if (supportedTypes.contains(AccountCredentials.Type.METRICS_STORE)) {
          accountCredentialsBuilder.datadogRemoteService(retrofitClientFactory.createClient(
            DatadogRemoteService.class,
            new JacksonConverter(objectMapper),
            account.getEndpoint(),
            okHttpClient
          ));
        }
        accountCredentialsBuilder.supportedTypes(supportedTypes);
      }

      accountCredentialsRepository.save(name, accountCredentialsBuilder.build());
      metricsServiceBuilder.accountName(name);
    }

    log.info("Populated DatadogMetricsService with {} Datadog accounts.", datadogConfigurationProperties.getAccounts().size());
    return metricsServiceBuilder.build();
  }
}

