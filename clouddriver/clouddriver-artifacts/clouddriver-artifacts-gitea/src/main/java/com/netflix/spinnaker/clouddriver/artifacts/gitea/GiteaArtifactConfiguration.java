/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.artifacts.gitea;

import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.netflix.spinnaker.credentials.definition.InvalidCredentialsConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.gitea.enabled")
@EnableConfigurationProperties(GiteaArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class GiteaArtifactConfiguration {
  private final GiteaArtifactProviderProperties giteaArtifactProviderProperties;

  @Bean
  OkHttpClient giteaOkHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  public CredentialsTypeProperties<GiteaArtifactCredentials, GiteaArtifactAccount>
      giteaCredentialsProperties(OkHttpClient giteaOkHttpClient) {
    return CredentialsTypeProperties.<GiteaArtifactCredentials, GiteaArtifactAccount>builder()
        .type(GiteaArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(GiteaArtifactCredentials.class)
        .credentialsDefinitionClass(GiteaArtifactAccount.class)
        .defaultCredentialsSource(giteaArtifactProviderProperties::getAccounts)
        .credentialsParser(
            a -> {
              try {
                return new GiteaArtifactCredentials(a, giteaOkHttpClient);
              } catch (InvalidCredentialsConfigurationException e) {
                throw e;
              } catch (Exception e) {
                log.warn("Failure instantiating Gitea artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }
}
