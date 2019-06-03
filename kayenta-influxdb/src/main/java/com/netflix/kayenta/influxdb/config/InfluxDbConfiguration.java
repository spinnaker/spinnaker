/*
 * Copyright 2018 Joseph Motha
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

package com.netflix.kayenta.influxdb.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.influxdb.metrics.InfluxDbMetricsService;
import com.netflix.kayenta.influxdb.security.InfluxdbCredentials;
import com.netflix.kayenta.influxdb.security.InfluxDbNamedAccountCredentials;
import com.netflix.kayenta.influxdb.service.InfluxDbRemoteService;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.influxdb.enabled")
@ComponentScan({"com.netflix.kayenta.influxdb"})
@Slf4j
public class InfluxDbConfiguration {
  @Bean
  @ConfigurationProperties("kayenta.influxdb")
  InfluxDbConfigurationProperties influxDbConfigurationProperties() {
    return new InfluxDbConfigurationProperties();
  }

  @Bean
  @ConfigurationProperties("kayenta.influxdb.test-controller-defaults")
  InfluxDbConfigurationTestControllerDefaultProperties influxDbConfigurationTestControllerDefaultProperties() {
    return new InfluxDbConfigurationTestControllerDefaultProperties();
  }

  @Bean
  MetricsService influxDbMetricsService(InfluxDbResponseConverter influxDbResponseConverter, InfluxDbConfigurationProperties influxDbConfigurationProperties, RetrofitClientFactory retrofitClientFactory, ObjectMapper objectMapper, OkHttpClient okHttpClient, AccountCredentialsRepository accountCredentialsRepository) throws IOException {
    InfluxDbMetricsService.InfluxDbMetricsServiceBuilder metricsServiceBuilder = InfluxDbMetricsService.builder();

    for (InfluxDbManagedAccount account : influxDbConfigurationProperties.getAccounts()) {
      String name = account.getName();
      List<AccountCredentials.Type> supportedTypes = account.getSupportedTypes();

      InfluxdbCredentials credentials = InfluxdbCredentials
        .builder()
        .build();

      InfluxDbNamedAccountCredentials.InfluxDbNamedAccountCredentialsBuilder accountCredentialsBuilder =
        InfluxDbNamedAccountCredentials
          .builder()
          .name(name)
          .endpoint(account.getEndpoint())
          .credentials(credentials);

      if (!CollectionUtils.isEmpty(supportedTypes)) {
        if (supportedTypes.contains(AccountCredentials.Type.METRICS_STORE)) {
          accountCredentialsBuilder.influxDbRemoteService(retrofitClientFactory.createClient(
            InfluxDbRemoteService.class,
            influxDbResponseConverter,
            account.getEndpoint(),
            okHttpClient
          ));
        }
        accountCredentialsBuilder.supportedTypes(supportedTypes);
      }

      accountCredentialsRepository.save(name, accountCredentialsBuilder.build());
      metricsServiceBuilder.accountName(name);
    }

    log.info("Populated influxDbMetricsService with {} influxdb accounts.", influxDbConfigurationProperties.getAccounts().size());
    return metricsServiceBuilder.build();
  }
}
