/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.config;

import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.prometheus.metrics.PrometheusMetricsService;
import com.netflix.kayenta.prometheus.security.PrometheusCredentials;
import com.netflix.kayenta.prometheus.security.PrometheusNamedAccountCredentials;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
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

import java.io.IOException;
import java.util.List;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.prometheus.enabled")
@ComponentScan({"com.netflix.kayenta.prometheus"})
@Slf4j
public class PrometheusConfiguration {

  @Bean
  @ConfigurationProperties("kayenta.prometheus")
  PrometheusConfigurationProperties prometheusConfigurationProperties() {
    return new PrometheusConfigurationProperties();
  }

  @Bean
  @ConfigurationProperties("kayenta.prometheus.testControllerDefaults")
  PrometheusConfigurationTestControllerDefaultProperties prometheusConfigurationTestControllerDefaultProperties() {
    return new PrometheusConfigurationTestControllerDefaultProperties();
  }

  @Bean
  MetricsService prometheusMetricsService(PrometheusResponseConverter prometheusConverter,
                                          PrometheusConfigurationProperties prometheusConfigurationProperties,
                                          RetrofitClientFactory retrofitClientFactory,
                                          OkHttpClient okHttpClient,
                                          AccountCredentialsRepository accountCredentialsRepository) throws IOException {
    PrometheusMetricsService.PrometheusMetricsServiceBuilder prometheusMetricsServiceBuilder = PrometheusMetricsService.builder();
    prometheusMetricsServiceBuilder.scopeLabel(prometheusConfigurationProperties.getScopeLabel());

    for (PrometheusManagedAccount prometheusManagedAccount : prometheusConfigurationProperties.getAccounts()) {
      String name = prometheusManagedAccount.getName();
      List<AccountCredentials.Type> supportedTypes = prometheusManagedAccount.getSupportedTypes();

      log.info("Registering Prometheus account {} with supported types {}.", name, supportedTypes);

      try {
        PrometheusCredentials prometheusCredentials =
          PrometheusCredentials
            .builder()
            .username(prometheusManagedAccount.getUsername())
            .password(prometheusManagedAccount.getPassword())
            .usernamePasswordFile(prometheusManagedAccount.getUsernamePasswordFile())
            .build();
        PrometheusNamedAccountCredentials.PrometheusNamedAccountCredentialsBuilder prometheusNamedAccountCredentialsBuilder =
          PrometheusNamedAccountCredentials
            .builder()
            .name(name)
            .endpoint(prometheusManagedAccount.getEndpoint())
            .credentials(prometheusCredentials);

        if (!CollectionUtils.isEmpty(supportedTypes)) {
          if (supportedTypes.contains(AccountCredentials.Type.METRICS_STORE)) {
            PrometheusRemoteService prometheusRemoteService = retrofitClientFactory.createClient(PrometheusRemoteService.class,
                                                                                                 prometheusConverter,
                                                                                                 prometheusManagedAccount.getEndpoint(),
                                                                                                 okHttpClient,
                                                                                                 prometheusManagedAccount.getUsername(),
                                                                                                 prometheusManagedAccount.getPassword(),
                                                                                                 prometheusManagedAccount.getUsernamePasswordFile());

            prometheusNamedAccountCredentialsBuilder.prometheusRemoteService(prometheusRemoteService);
          }

          prometheusNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
        }

        PrometheusNamedAccountCredentials prometheusNamedAccountCredentials = prometheusNamedAccountCredentialsBuilder.build();
        accountCredentialsRepository.save(name, prometheusNamedAccountCredentials);
        prometheusMetricsServiceBuilder.accountName(name);
      } catch (IOException e) {
        log.error("Problem registering Prometheus account {}:", name, e);
      }
    }

    PrometheusMetricsService prometheusMetricsService = prometheusMetricsServiceBuilder.build();

    log.info("Populated PrometheusMetricsService with {} Prometheus accounts.", prometheusMetricsService.getAccountNames().size());

    return prometheusMetricsService;
  }
}
