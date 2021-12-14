/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.health;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.core.AccountHealthIndicator;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Optional;

public class DockerRegistryHealthIndicator
    extends AccountHealthIndicator<DockerRegistryNamedAccountCredentials> {
  private static final String ID = "docker";
  private final CredentialsRepository<DockerRegistryNamedAccountCredentials> credentialsRepository;

  public DockerRegistryHealthIndicator(
      Registry registry,
      CredentialsRepository<DockerRegistryNamedAccountCredentials> credentialsRepository) {
    super(ID, registry);
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  protected ImmutableList<DockerRegistryNamedAccountCredentials> getAccounts() {
    return ImmutableList.copyOf(credentialsRepository.getAll());
  }

  @Override
  protected Optional<String> accountHealth(
      DockerRegistryNamedAccountCredentials accountCredentials) {
    try {
      accountCredentials.getCredentials().getClient().checkV2Availability();
      return Optional.empty();
    } catch (RuntimeException e) {
      return Optional.of(e.getMessage());
    }
  }
}
