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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.kubernetes;

import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("kubernetes.enabled")
@RequiredArgsConstructor
@Slf4j
class KubernetesArtifactConfiguration {
  @Bean
  public CredentialsRepository<KubernetesArtifactCredentials>
      kubernetesArtifactCredentialsRepository() {
    CredentialsRepository<KubernetesArtifactCredentials> repository =
        new MapBackedCredentialsRepository<>(
            KubernetesArtifactCredentials.CREDENTIALS_TYPE,
            new NoopCredentialsLifecycleHandler<>());
    repository.save(new KubernetesArtifactCredentials(new KubernetesArtifactAccount()));
    return repository;
  }
}
