/*
 * Copyright 2017 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.github;

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
@ConditionalOnProperty("artifacts.github.enabled")
@EnableScheduling
@Slf4j
public class GitHubArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.github")
  GitHubArtifactProviderProperties githubArtifactProviderProperties() {
    return new GitHubArtifactProviderProperties();
  }

  @Autowired
  GitHubArtifactProviderProperties gitHubArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Autowired
  ObjectMapper objectMapper;

  @Bean
  OkHttpClient gitHubOkHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  List<? extends GitHubArtifactCredentials> gitHubArtifactCredentials(OkHttpClient gitHubOkHttpClient) {
    return gitHubArtifactProviderProperties.getAccounts()
      .stream()
      .map(a -> {
        try {
          GitHubArtifactCredentials c = new GitHubArtifactCredentials(a, gitHubOkHttpClient, objectMapper);
          artifactCredentialsRepository.save(c);
          return c;
        } catch (Exception e) {
          log.warn("Failure instantiating GitHub artifact account {}: ", a, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
