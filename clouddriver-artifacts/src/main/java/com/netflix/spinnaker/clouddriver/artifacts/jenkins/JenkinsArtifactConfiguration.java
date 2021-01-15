/*
 * Copyright 2019 Pivotal, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.artifacts.jenkins;

import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.squareup.okhttp.OkHttpClient;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("jenkins.enabled")
@EnableConfigurationProperties(JenkinsProperties.class)
@RequiredArgsConstructor
@Slf4j
class JenkinsArtifactConfiguration {
  private final JenkinsProperties jenkinsProperties;

  @Bean
  public CredentialsTypeProperties<JenkinsArtifactCredentials, JenkinsArtifactAccount>
      jenkinsCredentialsProperties(OkHttpClient okHttpClient) {
    return CredentialsTypeProperties.<JenkinsArtifactCredentials, JenkinsArtifactAccount>builder()
        .type(JenkinsArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(JenkinsArtifactCredentials.class)
        .credentialsDefinitionClass(JenkinsArtifactAccount.class)
        .defaultCredentialsSource(
            () ->
                jenkinsProperties.getMasters().stream()
                    .map(
                        m ->
                            new JenkinsArtifactAccount(
                                m.getName(), m.getUsername(), m.getPassword(), m.getAddress()))
                    .collect(Collectors.toList()))
        .credentialsParser(
            a -> {
              try {
                return new JenkinsArtifactCredentials(a, okHttpClient);
              } catch (Exception e) {
                log.warn("Failure instantiating jenkins artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }
}
