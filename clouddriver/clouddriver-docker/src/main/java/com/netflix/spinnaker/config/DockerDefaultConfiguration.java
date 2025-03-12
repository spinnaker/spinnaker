/*
 * Copyright 2021 Armory
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "docker-registry.enabled",
    havingValue = "false",
    matchIfMissing = true)
class DockerDefaultConfiguration {

  // this bean will be created for DeployCloudFoundryServerGroupAtomicOperationConverter class
  // only if dockerRegistry is disabled
  @Bean
  @ConditionalOnProperty(value = "cloudfoundry.enabled", havingValue = "true")
  public CredentialsRepository<DockerRegistryNamedAccountCredentials>
      defaultDockerRegistryCredentialsRepository() {
    return new MapBackedCredentialsRepository<>(
        DockerRegistryProvider.PROVIDER_NAME, new NoopCredentialsLifecycleHandler<>());
  }
}
