/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.git-repo.enabled")
@EnableConfigurationProperties(GitRepoArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class GitRepoArtifactConfiguration {
  private final GitRepoArtifactProviderProperties gitRepoArtifactProviderProperties;

  @Bean
  public CredentialsTypeProperties<GitRepoArtifactCredentials, GitRepoArtifactAccount>
      gitCredentialsProperties(
          @Value("${artifacts.git-repo.git-executable:git}") String gitExecutable,
          JobExecutor jobExecutor) {
    return CredentialsTypeProperties.<GitRepoArtifactCredentials, GitRepoArtifactAccount>builder()
        .type(GitRepoArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(GitRepoArtifactCredentials.class)
        .credentialsDefinitionClass(GitRepoArtifactAccount.class)
        .defaultCredentialsSource(gitRepoArtifactProviderProperties::getAccounts)
        .credentialsParser(
            a -> {
              try {
                return new GitRepoArtifactCredentials(
                    new GitJobExecutor(a, jobExecutor, gitExecutable));
              } catch (IOException e) {
                log.warn("Failure instantiating git artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }
}
