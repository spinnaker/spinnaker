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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.s3.enabled")
@EnableConfigurationProperties(S3ArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class S3ArtifactConfiguration {
  private final S3ArtifactProviderProperties s3ArtifactProviderProperties;

  @Bean
  public CredentialsTypeProperties<S3ArtifactCredentials, S3ArtifactAccount>
      s3CredentialsProperties(
          Optional<S3ArtifactValidator> s3ArtifactValidator,
          S3ArtifactProviderProperties s3ArtifactProviderProperties,
          Registry registry) {
    return CredentialsTypeProperties.<S3ArtifactCredentials, S3ArtifactAccount>builder()
        .type(S3ArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(S3ArtifactCredentials.class)
        .credentialsDefinitionClass(S3ArtifactAccount.class)
        .defaultCredentialsSource(s3ArtifactProviderProperties::getAccounts)
        .credentialsParser(
            a -> {
              try {
                return new S3ArtifactCredentials(
                    a, s3ArtifactValidator, s3ArtifactProviderProperties, registry);
              } catch (Exception e) {
                log.warn("Failure instantiating s3 artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }
}
