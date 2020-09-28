/*
 * Copyright 2020 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
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
final class KubernetesHealthIndicatorTest {
  private static final String ERROR_MESSAGE = "Failed to get namespaces";
  private static final Registry REGISTRY = new NoopRegistry();
  private static final String HEALTHY_ACCOUNT_NAME = "healthy";
  private static final String UNHEALTHY_ACCOUNT_NAME_FIRST = "unhealthy1";
  private static final String UNHEALTHY_ACCOUNT_NAME_SECOND = "unhealthy2";

  private KubernetesNamedAccountCredentials healthyNamedCredentials;
  private KubernetesNamedAccountCredentials unhealthyNamedCredentialsFirst;
  private KubernetesNamedAccountCredentials unhealthyNamedCredentialsSecond;

  @Mock private KubernetesCredentials.Factory healthyCredentialsFactory;
  @Mock private KubernetesCredentials.Factory unhealthyCredentialsFactory;
  @Mock private KubernetesCredentials healthyCredentials;
  @Mock private KubernetesCredentials unhealthyCredentials;

  @BeforeEach
  void setup() {
    when(healthyCredentialsFactory.build(any())).thenReturn(healthyCredentials);
    when(unhealthyCredentialsFactory.build(any())).thenReturn(unhealthyCredentials);
    lenient()
        .when(unhealthyCredentials.getDeclaredNamespaces())
        .thenThrow(new RuntimeException(ERROR_MESSAGE));

    healthyNamedCredentials =
        new KubernetesNamedAccountCredentials(
            getManagedAccount(HEALTHY_ACCOUNT_NAME), healthyCredentialsFactory);
    unhealthyNamedCredentialsFirst =
        new KubernetesNamedAccountCredentials(
            getManagedAccount(UNHEALTHY_ACCOUNT_NAME_FIRST), unhealthyCredentialsFactory);
    unhealthyNamedCredentialsSecond =
        new KubernetesNamedAccountCredentials(
            getManagedAccount(UNHEALTHY_ACCOUNT_NAME_SECOND), unhealthyCredentialsFactory);
  }

  @Test
  void healthyWithNoAccounts() {
    CredentialsRepository<KubernetesNamedAccountCredentials> repository =
        stubCredentialsRepository(ImmutableList.of());

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void healthyWithOnlyHealthyAccounts() {
    CredentialsRepository<KubernetesNamedAccountCredentials> repository =
        stubCredentialsRepository(ImmutableList.of(healthyNamedCredentials));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void reportsErrorForUnhealthyAccount() {
    CredentialsRepository<KubernetesNamedAccountCredentials> repository =
        stubCredentialsRepository(ImmutableList.of(unhealthyNamedCredentialsFirst));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails())
        .containsOnly(entry(UNHEALTHY_ACCOUNT_NAME_FIRST, ERROR_MESSAGE));
  }

  @Test
  void reportsMultipleErrors() {
    CredentialsRepository<KubernetesNamedAccountCredentials> repository =
        stubCredentialsRepository(
            ImmutableList.of(
                healthyNamedCredentials,
                unhealthyNamedCredentialsFirst,
                unhealthyNamedCredentialsSecond));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, repository);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails())
        .containsOnly(
            entry(UNHEALTHY_ACCOUNT_NAME_FIRST, ERROR_MESSAGE),
            entry(UNHEALTHY_ACCOUNT_NAME_SECOND, ERROR_MESSAGE));
    assertThat(result.getDetails())
        .containsOnly(
            entry(UNHEALTHY_ACCOUNT_NAME_FIRST, ERROR_MESSAGE),
            entry(UNHEALTHY_ACCOUNT_NAME_SECOND, ERROR_MESSAGE));
  }

  private static KubernetesConfigurationProperties.ManagedAccount getManagedAccount(String name) {
    KubernetesConfigurationProperties.ManagedAccount managedAccount =
        new KubernetesConfigurationProperties.ManagedAccount();
    managedAccount.setName(name);
    return managedAccount;
  }

  private static CredentialsRepository<KubernetesNamedAccountCredentials> stubCredentialsRepository(
      Iterable<KubernetesNamedAccountCredentials> accounts) {
    CredentialsRepository<KubernetesNamedAccountCredentials> repository =
        new MapBackedCredentialsRepository<>(KubernetesCloudProvider.ID, null);
    for (KubernetesNamedAccountCredentials account : accounts) {
      repository.save(account);
    }
    return repository;
  }
}
