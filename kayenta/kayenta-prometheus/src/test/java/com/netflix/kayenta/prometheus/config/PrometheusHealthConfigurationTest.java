/*
 * Copyright 2020 Playtika.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class PrometheusHealthConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(
              PrometheusConfiguration.PrometheusHealthConfiguration.class,
              PrometheusMockConfiguration.class);

  @Test
  public void shouldNotCreateAnyBeansWhenDisabled() {
    this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(HealthIndicator.class));
  }

  @Test
  public void shouldNotCreateAnyBeansWhenNoAccountsConfigured() {
    this.contextRunner
        .withPropertyValues("kayenta.prometheus.health.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(HealthIndicator.class));
  }

  @Test
  public void shouldCreateHealthIndicatorWhenAccountConfiguredAndHealthEnabled() {
    this.contextRunner
        .withPropertyValues("spring.profiles.active=prometheusHealth")
        .run(context -> assertThat(context).hasBean("prometheusHealthIndicator"));
  }

  @EnableConfigurationProperties
  @Configuration(proxyBeanMethods = false)
  static class PrometheusMockConfiguration {

    @Bean
    MetricsService prometheusMetricsService() {
      return mock(MetricsService.class);
    }

    @Bean
    @ConfigurationProperties("kayenta.prometheus")
    PrometheusConfigurationProperties prometheusConfigurationProperties() {
      return new PrometheusConfigurationProperties();
    }

    @Bean
    AccountCredentialsRepository accountCredentialsRepository() {
      return mock(AccountCredentialsRepository.class);
    }
  }
}
