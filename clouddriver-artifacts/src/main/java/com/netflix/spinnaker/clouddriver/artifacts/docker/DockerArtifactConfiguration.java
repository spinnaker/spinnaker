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

package com.netflix.spinnaker.clouddriver.artifacts.docker;

import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("${kubernetes.enabled:false} || ${dockerRegistry.enabled:false}")
@RequiredArgsConstructor
@Slf4j
class DockerArtifactConfiguration {

  @Bean
  public CredentialsRepository<DockerArtifactCredentials> dockerArtifactCredentialsRepository() {
    CredentialsRepository<DockerArtifactCredentials> repository =
        new MapBackedCredentialsRepository<>(
            DockerArtifactCredentials.CREDENTIALS_TYPE, new NoopCredentialsLifecycleHandler<>());
    repository.save(new DockerArtifactCredentials(new DockerArtifactAccount()));
    return repository;
  }
}
