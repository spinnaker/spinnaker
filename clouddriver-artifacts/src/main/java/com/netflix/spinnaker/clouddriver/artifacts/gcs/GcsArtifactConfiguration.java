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

import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.gcs.enabled")
@EnableConfigurationProperties(GcsArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class GcsArtifactConfiguration {
  private final GcsArtifactProviderProperties gcsArtifactProviderProperties;

  @Bean
  public CredentialsTypeProperties<GcsArtifactCredentials, GcsArtifactAccount>
      gcsCredentialsProperties(String clouddriverUserAgentApplicationName) {
    return CredentialsTypeProperties.<GcsArtifactCredentials, GcsArtifactAccount>builder()
        .type(GcsArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(GcsArtifactCredentials.class)
        .credentialsDefinitionClass(GcsArtifactAccount.class)
        .defaultCredentialsSource(gcsArtifactProviderProperties::getAccounts)
        .credentialsParser(
            a -> {
              try {
                return new GcsArtifactCredentials(clouddriverUserAgentApplicationName, a);
              } catch (IOException | GeneralSecurityException e) {
                log.warn("Failure instantiating gcs artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }
}
