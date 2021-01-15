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
import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.squareup.okhttp.OkHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.github.enabled")
@EnableConfigurationProperties(GitHubArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class GitHubArtifactConfiguration {
  private final GitHubArtifactProviderProperties gitHubArtifactProviderProperties;

  @Bean
  public CredentialsTypeProperties<GitHubArtifactCredentials, GitHubArtifactAccount>
      githubCredentialsProperties(OkHttpClient okHttpClient, ObjectMapper objectMapper) {
    return CredentialsTypeProperties.<GitHubArtifactCredentials, GitHubArtifactAccount>builder()
        .type(GitHubArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(GitHubArtifactCredentials.class)
        .credentialsDefinitionClass(GitHubArtifactAccount.class)
        .defaultCredentialsSource(gitHubArtifactProviderProperties::getAccounts)
        .credentialsParser(
            a -> {
              try {
                return new GitHubArtifactCredentials(a, okHttpClient, objectMapper);
              } catch (Exception e) {
                log.warn("Failure instantiating GitHub artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }
}
