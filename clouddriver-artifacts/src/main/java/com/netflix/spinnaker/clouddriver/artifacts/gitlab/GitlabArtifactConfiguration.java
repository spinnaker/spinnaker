/*
 * Copyright 2018 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty("artifacts.gitlab.enabled")
@EnableScheduling
@Slf4j
public class GitlabArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.gitlab")
  GitlabArtifactProviderProperties gitlabArtifactProviderProperties() {
    return new GitlabArtifactProviderProperties();
  }

  @Autowired
  GitlabArtifactProviderProperties gitlabArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Autowired
  ObjectMapper objectMapper;

  @Bean
  OkHttpClient gitlabOkHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  List<? extends GitlabArtifactCredentials> gitlabArtifactCredentials(OkHttpClient gitlabOkHttpClient) {
    return gitlabArtifactProviderProperties.getAccounts()
      .stream()
      .map(a -> {
        try {
          GitlabArtifactCredentials c = new GitlabArtifactCredentials(a, gitlabOkHttpClient, objectMapper);
          artifactCredentialsRepository.save(c);
          return c;
        } catch (Exception e) {
          log.warn("Failure instantiating Gitlab artifact account {}: ", a, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
