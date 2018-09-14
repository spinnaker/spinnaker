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

package com.netflix.kayenta.atlas.config;

import com.netflix.kayenta.atlas.backends.BackendUpdater;
import com.netflix.kayenta.atlas.backends.BackendUpdaterService;
import com.netflix.kayenta.atlas.metrics.AtlasMetricsService;
import com.netflix.kayenta.atlas.security.AtlasCredentials;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@ConditionalOnProperty("kayenta.atlas.enabled")
@ComponentScan({"com.netflix.kayenta.atlas"})
@Slf4j
public class AtlasConfiguration {

  private final BackendUpdaterService backendUpdaterService;

  @Autowired
  public AtlasConfiguration(BackendUpdaterService backendUpdaterService) {
    this.backendUpdaterService = backendUpdaterService;
  }

  @Bean
  @ConfigurationProperties("kayenta.atlas")
  AtlasConfigurationProperties atlasConfigurationProperties() {
    return new AtlasConfigurationProperties();
  }

  @Bean
  MetricsService atlasMetricsService(AtlasConfigurationProperties atlasConfigurationProperties,
                                     AccountCredentialsRepository accountCredentialsRepository) {
    AtlasMetricsService.AtlasMetricsServiceBuilder atlasMetricsServiceBuilder = AtlasMetricsService.builder();

    for (AtlasManagedAccount atlasManagedAccount : atlasConfigurationProperties.getAccounts()) {
      String name = atlasManagedAccount.getName();
      List<AccountCredentials.Type> supportedTypes = atlasManagedAccount.getSupportedTypes();
      String backendsJsonUriPrefix = atlasManagedAccount.getBackendsJsonBaseUrl();

      log.info("Registering Atlas account {} with supported types {}.", name, supportedTypes);

      AtlasCredentials atlasCredentials =
        AtlasCredentials
          .builder()
          .build();

      BackendUpdater updater = BackendUpdater.builder().uri(backendsJsonUriPrefix).build();
      AtlasNamedAccountCredentials.AtlasNamedAccountCredentialsBuilder atlasNamedAccountCredentialsBuilder =
        AtlasNamedAccountCredentials
          .builder()
          .name(name)
          .credentials(atlasCredentials)
          .fetchId(atlasManagedAccount.getFetchId())
          .recommendedLocations(atlasManagedAccount.getRecommendedLocations())
          .backendUpdater(updater);

      if (!CollectionUtils.isEmpty(supportedTypes)) {
        atlasNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
      }

      AtlasNamedAccountCredentials atlasNamedAccountCredentials = atlasNamedAccountCredentialsBuilder.build();
      accountCredentialsRepository.save(name, atlasNamedAccountCredentials);
      atlasMetricsServiceBuilder.accountName(name);

      backendUpdaterService.add(atlasNamedAccountCredentials.getBackendUpdater());
    }

    AtlasMetricsService atlasMetricsService = atlasMetricsServiceBuilder.build();

    log.info("Populated AtlasMetricsService with {} Atlas accounts.", atlasMetricsService.getAccountNames().size());

    return atlasMetricsService;
  }
}
