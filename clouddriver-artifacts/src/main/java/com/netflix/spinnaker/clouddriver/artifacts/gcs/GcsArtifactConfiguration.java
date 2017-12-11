/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.gcs;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty("artifacts.gcs.enabled")
@EnableScheduling
@Slf4j
public class GcsArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.gcs")
  GcsArtifactProviderProperties gcsArtifactProviderProperties() {
    return new GcsArtifactProviderProperties();
  }

  @Autowired
  GcsArtifactProviderProperties gcsArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Bean
  List<? extends GcsArtifactCredentials> gcsArtifactCredentials(String clouddriverUserAgentApplicationName) {
    return gcsArtifactProviderProperties.getAccounts()
        .stream()
        .map(a -> {
          try {
            GcsArtifactCredentials c = new GcsArtifactCredentials(clouddriverUserAgentApplicationName, a);
            artifactCredentialsRepository.save(c);
            return c;
          } catch (IOException | GeneralSecurityException e) {
            log.warn("Failure instantiating gcs artifact account {}: ", a, e);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
