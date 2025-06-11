/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.docker;

import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression(
    "${kubernetes.enabled:false} || ${cloudrun.enabled:false} || ${dockerRegistry.enabled:false}")
@EnableConfigurationProperties({
  HelmOciDockerArtifactProviderProperties.class,
  HelmOciArtifactProviderProperties.class
})
@Slf4j
class HelmOciArtifactConfiguration {

  @Bean
  public CredentialsTypeProperties<HelmOciDockerArtifactCredentials, HelmOciDockerArtifactAccount>
      helmOciDockerCredentialsProperties(
          OkHttpClient okHttpClient,
          HelmOciFileSystem helmChartsFileSystem,
          ServiceClientProvider serviceClientProvider,
          HelmOciDockerArtifactProviderProperties helmOciDockerArtifactProviderProperties) {
    return CredentialsTypeProperties
        .<HelmOciDockerArtifactCredentials, HelmOciDockerArtifactAccount>builder()
        .type(HelmOciDockerArtifactCredentials.CREDENTIALS_TYPE)
        .credentialsClass(HelmOciDockerArtifactCredentials.class)
        .credentialsDefinitionClass(HelmOciDockerArtifactAccount.class)
        .defaultCredentialsSource(helmOciDockerArtifactProviderProperties::getAccounts)
        .credentialsParser(
            a -> {
              try {
                return new HelmOciDockerArtifactCredentials(
                    a, okHttpClient, helmChartsFileSystem, serviceClientProvider);
              } catch (Exception e) {
                log.warn("Failure instantiating Docker artifact account {}: ", a, e);
                return null;
              }
            })
        .build();
  }

  @Bean
  public HelmOciFileSystem helmChartsFileSystem(
      HelmOciArtifactProviderProperties helmOciArtifactProviderProperties) {
    return new HelmOciFileSystem(helmOciArtifactProviderProperties);
  }
}
