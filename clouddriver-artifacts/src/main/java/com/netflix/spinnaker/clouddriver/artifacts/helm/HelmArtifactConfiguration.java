/*
 * Copyright 2018 Mirantis, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.helm;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
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
@ConditionalOnProperty("artifacts.helm.enabled")
@EnableScheduling
@Slf4j
public class HelmArtifactConfiguration {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.helm")
  HelmArtifactProviderProperties HelmArtifactProviderProperties() {
    return new HelmArtifactProviderProperties();
  }

  @Autowired
  HelmArtifactProviderProperties helmArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Autowired
  OkHttpClient helmOkHttpClient;

  @Bean
  OkHttpClient helmOkHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  List<? extends HelmArtifactCredentials> helmArtifactCredentials() {
    List<HelmArtifactCredentials> result = helmArtifactProviderProperties.getAccounts()
      .stream()
      .map(a -> {
        try {
          HelmArtifactCredentials c = new HelmArtifactCredentials(a, helmOkHttpClient);
          artifactCredentialsRepository.save(c);
          return c;
        } catch (Exception e) {
          log.warn("Failure instantiating Helm artifact account {}: ", a, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    return result;
  }
}
