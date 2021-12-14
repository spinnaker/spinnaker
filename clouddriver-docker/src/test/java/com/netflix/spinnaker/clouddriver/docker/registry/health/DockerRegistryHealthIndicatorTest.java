/*
 * Copyright 2021 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.docker.registry.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
class DockerRegistryHealthIndicatorTest {

  private static final String ERROR_MESSAGE = "Failed to get namespaces";
  private static final String HEALTHY_ACCOUNT_NAME = "healthy";
  private static final String UNHEALTHY_ACCOUNT_NAME_FIRST = "unhealthy1";
  private static final String UNHEALTHY_ACCOUNT_NAME_SECOND = "unhealthy2";
  private static final String CREDENTIALS_TYPE = "dockerRegistry";

  private static final Registry REGISTRY = new NoopRegistry();

  @Mock private DockerRegistryNamedAccountCredentials healthyNamedCredentials;
  @Mock private DockerRegistryNamedAccountCredentials unhealthyNamedAccountCredentialsFirst;
  @Mock private DockerRegistryNamedAccountCredentials unhealthyNamedAccountCredentialsSecond;

  @Mock private DockerRegistryCredentials dockerRegistryCredentials;

  @Mock private DockerRegistryClient dockerRegistryClient;

  @Test
  void healthyWithNoAccounts() {
    CredentialsRepository<DockerRegistryNamedAccountCredentials> repository =
        stubCredentialsRepository(ImmutableList.of());

    DockerRegistryHealthIndicator healthIndicator =
        new DockerRegistryHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void healthyWithOnlyHealthyAccounts() {
    when(healthyNamedCredentials.getCredentials()).thenReturn(dockerRegistryCredentials);
    when(healthyNamedCredentials.getName()).thenReturn(HEALTHY_ACCOUNT_NAME);
    when(healthyNamedCredentials.getType()).thenReturn(CREDENTIALS_TYPE);
    when(dockerRegistryCredentials.getClient()).thenReturn(dockerRegistryClient);

    // no exception if account is healthy
    doNothing().when(dockerRegistryClient).checkV2Availability();

    CredentialsRepository<DockerRegistryNamedAccountCredentials> repository =
        stubCredentialsRepository(ImmutableList.of(healthyNamedCredentials));

    DockerRegistryHealthIndicator healthIndicator =
        new DockerRegistryHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void reportsErrorForUnhealthyAccount() {
    when(unhealthyNamedAccountCredentialsFirst.getCredentials())
        .thenReturn(dockerRegistryCredentials);
    when(unhealthyNamedAccountCredentialsFirst.getName()).thenReturn(UNHEALTHY_ACCOUNT_NAME_FIRST);
    when(unhealthyNamedAccountCredentialsFirst.getType()).thenReturn(CREDENTIALS_TYPE);
    when(dockerRegistryCredentials.getClient()).thenReturn(dockerRegistryClient);
    // exception thrown because the account is unhealthy
    doThrow(new RuntimeException(ERROR_MESSAGE)).when(dockerRegistryClient).checkV2Availability();

    CredentialsRepository<DockerRegistryNamedAccountCredentials> repository =
        stubCredentialsRepository(ImmutableList.of(unhealthyNamedAccountCredentialsFirst));

    DockerRegistryHealthIndicator healthIndicator =
        new DockerRegistryHealthIndicator(REGISTRY, repository);
    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertEquals(1, result.getDetails().size());
    assertTrue(
        result.getDetails().containsKey(UNHEALTHY_ACCOUNT_NAME_FIRST)
            && result.getDetails().get(UNHEALTHY_ACCOUNT_NAME_FIRST).equals(ERROR_MESSAGE));
  }

  @Test
  void reportsMultipleErrors() {
    when(healthyNamedCredentials.getCredentials()).thenReturn(dockerRegistryCredentials);
    when(healthyNamedCredentials.getName()).thenReturn(HEALTHY_ACCOUNT_NAME);
    when(healthyNamedCredentials.getType()).thenReturn(CREDENTIALS_TYPE);
    when(dockerRegistryCredentials.getClient()).thenReturn(dockerRegistryClient);

    DockerRegistryCredentials unhealthyDockerRegistryCredentials =
        mock(DockerRegistryCredentials.class);
    DockerRegistryClient unhealthyDockerRegistryClient = mock(DockerRegistryClient.class);

    when(unhealthyNamedAccountCredentialsFirst.getCredentials())
        .thenReturn(unhealthyDockerRegistryCredentials);
    when(unhealthyNamedAccountCredentialsFirst.getName()).thenReturn(UNHEALTHY_ACCOUNT_NAME_FIRST);
    when(unhealthyNamedAccountCredentialsFirst.getType()).thenReturn(CREDENTIALS_TYPE);
    when(unhealthyDockerRegistryCredentials.getClient()).thenReturn(unhealthyDockerRegistryClient);

    when(unhealthyNamedAccountCredentialsSecond.getCredentials())
        .thenReturn(unhealthyDockerRegistryCredentials);
    when(unhealthyNamedAccountCredentialsSecond.getName())
        .thenReturn(UNHEALTHY_ACCOUNT_NAME_SECOND);
    when(unhealthyNamedAccountCredentialsSecond.getType()).thenReturn(CREDENTIALS_TYPE);
    when(unhealthyDockerRegistryCredentials.getClient()).thenReturn(unhealthyDockerRegistryClient);

    // no exception if account is healthy
    doNothing().when(dockerRegistryClient).checkV2Availability();
    // exception thrown because the account is unhealthy
    doThrow(new RuntimeException(ERROR_MESSAGE))
        .when(unhealthyDockerRegistryClient)
        .checkV2Availability();

    CredentialsRepository<DockerRegistryNamedAccountCredentials> repository =
        stubCredentialsRepository(
            ImmutableList.of(
                healthyNamedCredentials,
                unhealthyNamedAccountCredentialsFirst,
                unhealthyNamedAccountCredentialsSecond));

    DockerRegistryHealthIndicator healthIndicator =
        new DockerRegistryHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertEquals(2, result.getDetails().size());
    assertTrue(
        result.getDetails().containsKey(UNHEALTHY_ACCOUNT_NAME_FIRST)
            && result.getDetails().get(UNHEALTHY_ACCOUNT_NAME_FIRST).equals(ERROR_MESSAGE));
    assertTrue(
        result.getDetails().containsKey(UNHEALTHY_ACCOUNT_NAME_SECOND)
            && result.getDetails().get(UNHEALTHY_ACCOUNT_NAME_SECOND).equals(ERROR_MESSAGE));
  }

  private static CredentialsRepository<DockerRegistryNamedAccountCredentials>
      stubCredentialsRepository(Iterable<DockerRegistryNamedAccountCredentials> accounts) {
    CredentialsRepository<DockerRegistryNamedAccountCredentials> repository =
        new MapBackedCredentialsRepository<>(
            DockerRegistryCloudProvider.getDOCKER_REGISTRY(), null);
    for (DockerRegistryNamedAccountCredentials account : accounts) {
      repository.save(account);
    }
    return repository;
  }
}
