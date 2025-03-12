/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.front50;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
class Front50ArtifactConfiguration {
  @Bean
  public CredentialsRepository<Front50ArtifactCredentials> front50ArtifactCredentialsRepository(
      ObjectMapper objectMapper, Front50Service front50Service) {
    CredentialsRepository<Front50ArtifactCredentials> repository =
        new MapBackedCredentialsRepository<>(
            Front50ArtifactCredentials.CREDENTIALS_TYPE, new NoopCredentialsLifecycleHandler<>());
    repository.save(new Front50ArtifactCredentials(objectMapper, front50Service));
    return repository;
  }
}
