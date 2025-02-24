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
import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperites;
import com.netflix.spinnaker.kork.actuator.observability.newrelic.NewRelicRegistrySupplier;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.PrometheusRegistrySupplier;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.PrometheusScrapeEndpoint;
import com.netflix.spinnaker.kork.actuator.observability.registry.AddDefaultTagsRegistryCustomizer;
import com.netflix.spinnaker.kork.actuator.observability.registry.AddFiltersRegistryCustomizer;
import com.netflix.spinnaker.kork.actuator.observability.registry.ArmoryObservabilityCompositeRegistry;
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
import org.springframework.beans.factory.annotation.Value;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 66)
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true")
public class ObservabilityConfiguration {

  public ObservabilityConfiguration() {
    log.info("Observability enabled");
  }

  @Bean
  public ObservabilityConfigurationProperites observabilityConfigurationProperites() {
    return new ObservabilityConfigurationProperites();
  }

  @Bean
  @ConditionalOnMissingBean(VersionResolver.class)
  public static VersionResolver versionResolver(ApplicationContext applicationContext) {
    return new SpringPackageVersionResolver(applicationContext);
  }

  @Bean
  public TagsService tagsService(
      ObservabilityConfigurationProperites observabilityConfigurationProperites,
      VersionResolver versionResolver,
      @Value("${spring.application.name:#{null}}") String springInjectedApplicationName) {
    return new TagsService(
        observabilityConfigurationProperites, versionResolver, springInjectedApplicationName);
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
  public CollectorRegistry collectorRegistry() {
    return new CollectorRegistry();
  }

  @Bean
  public PrometheusRegistrySupplier prometheusRegistrySupplier(
      ObservabilityConfigurationProperites pluginConfig,
      CollectorRegistry collectorRegistry,
      Clock clock) {
    return new PrometheusRegistrySupplier(pluginConfig, collectorRegistry, clock);
  }

  @Bean
  @Primary
  public ArmoryObservabilityCompositeRegistry armoryObservabilityCompositeRegistry(
      Clock clock,
      Collection<Supplier<RegistryConfigWrapper>> registrySuppliers,
      Collection<RegistryCustomizer> meterRegistryCustomizers) {
    return new ArmoryObservabilityCompositeRegistry(
        clock, registrySuppliers, meterRegistryCustomizers);
  }

  @Bean
  public PrometheusScrapeEndpoint prometheusScrapeEndpoint(CollectorRegistry collectorRegistry) {
    return new PrometheusScrapeEndpoint(collectorRegistry);
  }

  @Bean
  public DataDogRegistrySupplier dataDogRegistrySupplier(
      @NotNull ObservabilityConfigurationProperites pluginConfig) {
    return new DataDogRegistrySupplier(pluginConfig);
  }

  @Bean
  public NewRelicRegistrySupplier newRelicRegistrySupplier(
      ObservabilityConfigurationProperites pluginConfig, TagsService tagsService) {
    return new NewRelicRegistrySupplier(pluginConfig, tagsService);
  }
}
