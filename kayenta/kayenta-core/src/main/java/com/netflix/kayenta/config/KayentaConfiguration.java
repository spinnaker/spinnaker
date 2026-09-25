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

package com.netflix.kayenta.config;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.google.common.collect.ImmutableList;
import com.netflix.kayenta.atlas.config.KayentaSerializationConfigurationProperties;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.MapBackedMetricsServiceRepository;
import com.netflix.kayenta.metrics.MetricSetMixerService;
import com.netflix.kayenta.metrics.MetricsRetryConfigurationProperties;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.netflix.kayenta.service.MetricSetPairListService;
import com.netflix.kayenta.storage.MapBackedStorageServiceRepository;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

@Configuration
@Slf4j
@ComponentScan({
  "com.netflix.kayenta.canary",
  "com.netflix.kayenta.config",
  "com.netflix.kayenta.events",
  "com.netflix.kayenta.external",
  "com.netflix.kayenta.index.config",
  "com.netflix.kayenta.metrics",
  "com.netflix.kayenta.persistence.config",
  "com.netflix.kayenta.retrofit.config"
})
@EnableConfigurationProperties(MetricsRetryConfigurationProperties.class)
public class KayentaConfiguration {

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsRepository.class)
  AccountCredentialsRepository accountCredentialsRepository() {
    return new MapBackedAccountCredentialsRepository();
  }

  @Bean
  @ConditionalOnMissingBean(MetricsServiceRepository.class)
  MetricsServiceRepository metricsServiceRepository() {
    return new MapBackedMetricsServiceRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  MetricSetMixerService metricSetMixerService() {
    return new MetricSetMixerService();
  }

  @Bean
  @ConditionalOnMissingBean(StorageServiceRepository.class)
  StorageServiceRepository storageServiceRepository(
      @Autowired(required = false) Optional<List<StorageService>> storageServices) {
    return new MapBackedStorageServiceRepository(storageServices.orElse(Collections.emptyList()));
  }

  @Bean
  @ConditionalOnMissingBean
  MetricSetPairListService metricSetPairListService(
      AccountCredentialsRepository accountCredentialsRepository,
      StorageServiceRepository storageServiceRepository) {
    return new MetricSetPairListService(accountCredentialsRepository, storageServiceRepository);
  }

  //
  // Configure subtypes of CanaryMetricSetQueryConfig, all of which must be
  // defined in the package com.netflix.kayenta.canary.providers
  //
  @Bean
  public ObjectMapperSubtypeConfigurer.ClassSubtypeLocator assetSpecSubTypeLocator() {
    return new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
        CanaryMetricSetQueryConfig.class,
        ImmutableList.of("com.netflix.kayenta.canary.providers.metrics"));
  }

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapperSubtypeConfigurer objectMapperSubtypeConfigurer() {
    return new ObjectMapperSubtypeConfigurer(true);
  }

  @Primary
  @Bean
  ObjectMapper kayentaObjectMapper(
      ObjectMapper mapper,
      ObjectMapperSubtypeConfigurer objectMapperSubtypeConfigurer,
      List<ObjectMapperSubtypeConfigurer.SubtypeLocator> subtypeLocators,
      KayentaSerializationConfigurationProperties kayentaSerializationConfigurationProperties) {
    return configureObjectMapper(
        mapper,
        objectMapperSubtypeConfigurer,
        subtypeLocators,
        kayentaSerializationConfigurationProperties);
  }

  public static ObjectMapper configureObjectMapperFeatures(
      ObjectMapper objectMapper,
      KayentaSerializationConfigurationProperties kayentaSerializationConfigurationProperties) {
    return objectMapper
        .rebuild()
        .changeDefaultPropertyInclusion(
            value -> value.withValueInclusion(NON_NULL).withContentInclusion(NON_NULL))
        .disable(FAIL_ON_UNKNOWN_PROPERTIES)
        // Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default (Jackson 2 had it
        // disabled); missing primitives (e.g. Atlas close messages) must stay lenient.
        .disable(FAIL_ON_NULL_FOR_PRIMITIVES)
        .configure(
            DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
            kayentaSerializationConfigurationProperties.isWriteDatesAsTimestamps())
        .configure(
            DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS,
            kayentaSerializationConfigurationProperties.isWriteDurationsAsTimestamps())
        .build();
  }

  private ObjectMapper configureObjectMapper(
      ObjectMapper objectMapper,
      ObjectMapperSubtypeConfigurer objectMapperSubtypeConfigurer,
      List<ObjectMapperSubtypeConfigurer.SubtypeLocator> subtypeLocators,
      KayentaSerializationConfigurationProperties kayentaSerializationConfigurationProperties) {
    ObjectMapper configured =
        objectMapperSubtypeConfigurer.registerSubtypes(objectMapper, subtypeLocators);
    return configureObjectMapperFeatures(configured, kayentaSerializationConfigurationProperties);
  }

  @Bean
  @ConfigurationProperties(prefix = "kayenta.serialization")
  KayentaSerializationConfigurationProperties kayentaSerializationConfigurationProperties() {
    return new KayentaSerializationConfigurationProperties();
  }
}
