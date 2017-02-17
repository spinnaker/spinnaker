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

package com.netflix.spinnaker.kayenta.google.config;

import com.netflix.spinnaker.kayenta.google.security.GoogleCredentials;
import com.netflix.spinnaker.kayenta.google.security.GoogleJsonCredentials;
import com.netflix.spinnaker.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.kayenta.security.AccountCredentials;
import com.netflix.spinnaker.kayenta.security.AccountCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.google.enabled")
@ComponentScan({"com.netflix.spinnaker.kayenta.google"})
@Slf4j
public class GoogleConfiguration {

  @Bean
  @ConfigurationProperties("kayenta.google")
  GoogleConfigurationProperties googleConfigurationProperties() {
    return new GoogleConfigurationProperties();
  }

  @Bean
  boolean registerGoogleCredentials(GoogleConfigurationProperties googleConfigurationProperties, AccountCredentialsRepository accountCredentialsRepository) throws IOException {
    for (GoogleManagedAccount googleManagedAccount : googleConfigurationProperties.getAccounts()) {
      String name = googleManagedAccount.getName();
      String project = googleManagedAccount.getProject();
      List<AccountCredentials.Type> supportedTypes = googleManagedAccount.getSupportedTypes();

      log.info("Registering Google account {} for project {} of type {}.", name, project, AccountCredentials.Type.METRICS_STORE);

      try {
        String jsonKey = googleManagedAccount.getJsonKey();
        GoogleCredentials googleCredentials =
          StringUtils.hasLength(jsonKey)
          ? new GoogleJsonCredentials(project, jsonKey)
          : new GoogleCredentials(project);

        GoogleNamedAccountCredentials.GoogleNamedAccountCredentialsBuilder googleNamedAccountCredentialsBuilder =
          GoogleNamedAccountCredentials
            .builder()
            .name(name)
            .project(project)
            .credentials(googleCredentials)
            .monitoring(googleCredentials.getMonitoring("the-spinnaker-user-agent"));

        if (!CollectionUtils.isEmpty(supportedTypes)) {
          googleNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
        }

        GoogleNamedAccountCredentials googleNamedAccountCredentials = googleNamedAccountCredentialsBuilder.build();
        accountCredentialsRepository.save(name, googleNamedAccountCredentials);
      } catch (Throwable t) {
        log.error("Could not load Google account " + name + ".", t);
      }
    }

    return true;
  }
}
