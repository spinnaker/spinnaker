/*
 * Copyright 2018 Datadog, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty("artifacts.s3.enabled")
@EnableScheduling
@Slf4j
public class S3ArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.s3")
  S3ArtifactProviderProperties s3ArtifactProviderProperties() { return new S3ArtifactProviderProperties(); }

  @Autowired
  S3ArtifactProviderProperties s3ArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Bean
  List<? extends S3ArtifactCredentials> s3ArtifactCredentials() {
    return s3ArtifactProviderProperties.getAccounts()
      .stream()
      .map(a -> {
        try {
          S3ArtifactCredentials c = new S3ArtifactCredentials(a);
          artifactCredentialsRepository.save(c);
          return c;
        } catch (IllegalArgumentException e) {
          log.warn("Failure instantiating s3 artifact account {}: ", a, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
