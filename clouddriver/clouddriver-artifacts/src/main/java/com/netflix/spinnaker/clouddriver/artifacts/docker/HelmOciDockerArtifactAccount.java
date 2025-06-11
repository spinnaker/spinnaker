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

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.docker.service.DefaultDockerOkClientProvider;
import com.netflix.spinnaker.kork.docker.service.DockerRegistryClient;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

/**
 * TODO: This class duplicates many properties from DockerRegistryClient and
 * DockerRegistryNamedAccountCredentials. Future refactoring needed to reduce duplication.
 */
@NonnullByDefault
@Value
public class HelmOciDockerArtifactAccount implements ArtifactAccount {
  private final String name;

  private final String username;
  private final String password;
  private final File passwordFile;
  private final String passwordCommand;
  private final File dockerconfigFile;
  private final String email;
  private final String address;
  private final boolean insecureRegistry;
  private final List<String> helmOciRepositories;
  private final long clientTimeoutMillis;
  private final int paginateSize;

  private final ServiceClientProvider serviceClientProvider;

  private final String type = HelmOciDockerArtifactCredentials.CREDENTIALS_TYPE;
  private final String provider = HelmOciDockerArtifactCredentials.TYPE;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  HelmOciDockerArtifactAccount(
      String name,
      String username,
      String password,
      File passwordFile,
      String passwordCommand,
      File dockerconfigFile,
      String email,
      String address,
      boolean insecureRegistry,
      List<String> helmOciRepositories,
      long clientTimeoutMillis,
      int paginateSize,
      ServiceClientProvider serviceClientProvider) {
    this.name = Strings.nullToEmpty(name);
    this.username = username;
    this.password = password;
    this.passwordFile = passwordFile;
    this.passwordCommand = passwordCommand;
    this.dockerconfigFile = dockerconfigFile;
    this.email = email;
    this.address = address;
    this.insecureRegistry = insecureRegistry;
    this.helmOciRepositories = Optional.ofNullable(helmOciRepositories).orElse(List.of());
    this.clientTimeoutMillis =
        Optional.of(clientTimeoutMillis).orElse(TimeUnit.MINUTES.toMillis(1));
    this.paginateSize = Optional.of(paginateSize).orElse(100);
    this.serviceClientProvider = serviceClientProvider;
  }

  public DockerRegistryClient getDockerRegistryClient(ServiceClientProvider serviceClientProvider) {
    DockerRegistryClient.Builder builder =
        new DockerRegistryClient.Builder()
            .address(address)
            .email(email)
            .username(username)
            .password(password)
            .passwordCommand(passwordCommand)
            .passwordFile(passwordFile)
            .clientTimeoutMillis(clientTimeoutMillis)
            .paginateSize(paginateSize)
            .dockerconfigFile(dockerconfigFile)
            .insecureRegistry(insecureRegistry)
            .okClientProvider(new DefaultDockerOkClientProvider())
            .serviceClientProvider(serviceClientProvider);

    return builder.build();
  }
}
