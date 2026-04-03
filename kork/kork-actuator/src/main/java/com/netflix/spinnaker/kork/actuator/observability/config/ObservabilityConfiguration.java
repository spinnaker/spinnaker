/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.config;

import com.netflix.spinnaker.kork.actuator.observability.datadog.DataDogRegistrySupplier;
import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperties;
import com.netflix.spinnaker.kork.actuator.observability.newrelic.NewRelicRegistrySupplier;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.PrometheusRegistrySupplier;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.PrometheusScrapeEndpoint;
import com.netflix.spinnaker.kork.actuator.observability.registry.AddDefaultTagsRegistryCustomizer;
import com.netflix.spinnaker.kork.actuator.observability.registry.AddFiltersRegistryCustomizer;
import com.netflix.spinnaker.kork.actuator.observability.registry.ObservabilityCompositeRegistry;
import com.netflix.spinnaker.kork.actuator.observability.registry.RegistryConfigWrapper;
import com.netflix.spinnaker.kork.actuator.observability.registry.RegistryCustomizer;
import com.netflix.spinnaker.kork.actuator.observability.service.MeterFilterService;
import com.netflix.spinnaker.kork.actuator.observability.service.TagsService;
import com.netflix.spinnaker.kork.actuator.observability.version.SpringPackageVersionResolver;
import com.netflix.spinnaker.kork.actuator.observability.version.VersionResolver;
import io.micrometer.core.instrument.Clock;
import io.prometheus.client.CollectorRegistry;
import java.util.Collection;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Slf4j
@Configuration
@AutoConfigureBefore(MetricsAutoConfiguration.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 66)
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true")
public class ObservabilityConfiguration {

  public ObservabilityConfiguration() {
    log.info("Observability enabled");
  }

  @Bean
  public ObservabilityConfigurationProperties observabilityConfigurationProperties() {
    return new ObservabilityConfigurationProperties();
  }

  @Bean
  @ConditionalOnMissingBean(VersionResolver.class)
  public static VersionResolver versionResolver(ApplicationContext applicationContext) {
    return new SpringPackageVersionResolver(applicationContext);
  }

  @Bean
  public TagsService tagsService(
      ObservabilityConfigurationProperties observabilityConfigurationProperties,
      VersionResolver versionResolver,
      @Value("${spring.application.name:#{null}}") String springInjectedApplicationName) {
    return new TagsService(
        observabilityConfigurationProperties, versionResolver, springInjectedApplicationName);
  }

  @Bean
  public AddDefaultTagsRegistryCustomizer addDefaultTagsRegistryCustomizer(
      TagsService tagsService) {
    return new AddDefaultTagsRegistryCustomizer(tagsService);
  }

  @Bean
  public MeterFilterService meterFilterService() {
    return new MeterFilterService();
  }

  @Bean
  public AddFiltersRegistryCustomizer addFiltersRegistryCustomizer(
      MeterFilterService meterFilterService) {
    return new AddFiltersRegistryCustomizer(meterFilterService);
  }

  @Bean
  public InitializingBean overridePrimaryRegistryCompatibilityValidator(
      ObservabilityConfigurationProperties observabilityConfigurationProperties,
      @Value("${observability.config.override-primary-registry:true}") boolean overridePrimary) {
    return () -> {
      if (!overridePrimary
          && observabilityConfigurationProperties.getMetrics().getNewrelic().isEnabled()) {
        throw new IllegalStateException(
            "observability.config.metrics.newrelic.enabled=true is not supported when "
                + "observability.config.override-primary-registry=false");
      }
    };
  }

  @Bean
  @ConditionalOnProperty(
      name = "observability.config.metrics.prometheus.enabled",
      havingValue = "true")
  public CollectorRegistry collectorRegistry() {
    return new CollectorRegistry();
  }

  @Bean
  @ConditionalOnProperty(
      name = "observability.config.metrics.prometheus.enabled",
      havingValue = "true")
  public PrometheusRegistrySupplier prometheusRegistrySupplier(
      ObservabilityConfigurationProperties pluginConfig,
      CollectorRegistry collectorRegistry,
      Clock clock) {
    return new PrometheusRegistrySupplier(pluginConfig, collectorRegistry, clock);
  }

  @Bean
  @Primary
  @ConditionalOnProperty(
      name = "observability.config.override-primary-registry",
      havingValue = "true",
      matchIfMissing = true)
  public ObservabilityCompositeRegistry observabilityCompositeRegistry(
      Clock clock,
      Collection<Supplier<RegistryConfigWrapper>> registrySuppliers,
      Collection<RegistryCustomizer> meterRegistryCustomizers) {
    return new ObservabilityCompositeRegistry(clock, registrySuppliers, meterRegistryCustomizers);
  }

  /** Nested configuration to combine multiple ConditionalOnProperty conditions. */
  @Configuration
  @ConditionalOnProperty(
      name = "observability.config.metrics.prometheus.enabled",
      havingValue = "true")
  public static class PrometheusScrapeEndpointConfiguration {

    @Bean
    @ConditionalOnProperty(
        name = "observability.config.override-primary-registry",
        havingValue = "true",
        matchIfMissing = true)
    public PrometheusScrapeEndpoint prometheusScrapeEndpoint(CollectorRegistry collectorRegistry) {
      return new PrometheusScrapeEndpoint(collectorRegistry);
    }
  }

  @Bean
  public DataDogRegistrySupplier dataDogRegistrySupplier(
      @NotNull ObservabilityConfigurationProperties pluginConfig) {
    return new DataDogRegistrySupplier(pluginConfig);
  }

  @Bean
  public NewRelicRegistrySupplier newRelicRegistrySupplier(
      ObservabilityConfigurationProperties pluginConfig, TagsService tagsService) {
    return new NewRelicRegistrySupplier(pluginConfig, tagsService);
  }
}
