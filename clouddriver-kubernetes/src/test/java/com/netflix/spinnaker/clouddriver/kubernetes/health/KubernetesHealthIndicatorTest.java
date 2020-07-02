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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentialFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@RunWith(JUnitPlatform.class)
final class KubernetesHealthIndicatorTest {
  private static final ImmutableList<String> NAMESPACES = ImmutableList.of();
  private static final String ERROR_MESSAGE = "Failed to get namespaces";
  private static final Registry REGISTRY = new NoopRegistry();

  private static final KubernetesCredentialFactory<KubernetesCredentials>
      HEALTHY_CREDENTIAL_FACTORY =
          StubKubernetesCredentialsFactory.getInstance(
              StubKubernetesCredentials.withNamespaces(NAMESPACES));

  private static final KubernetesCredentialFactory<KubernetesCredentials>
      UNHEALTHY_CREDENTIAL_FACTORY =
          StubKubernetesCredentialsFactory.getInstance(
              StubKubernetesCredentials.withNamespaceException(
                  new RuntimeException(ERROR_MESSAGE)));

  @Test
  void healthyWithNoAccounts() {
    AccountCredentialsProvider provider = stubAccountCredentialsProvider(ImmutableList.of());

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, provider);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void healthyWithNoKubernetesAccounts() {
    AccountCredentialsProvider provider =
        stubAccountCredentialsProvider(
            ImmutableList.of(nonKubernetesAccount("aws"), nonKubernetesAccount("gce")));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, provider);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void healthyWithOnlyHealthyAccounts() {
    AccountCredentialsProvider provider =
        stubAccountCredentialsProvider(ImmutableList.of(healthyAccount("test")));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, provider);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).isEmpty();
  }

  @Test
  void reportsErrorForUnhealthyAccount() {
    String unhealthy = "unhealthy";
    AccountCredentialsProvider provider =
        stubAccountCredentialsProvider(ImmutableList.of(unhealthyAccount(unhealthy)));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, provider);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails()).containsOnly(entry(unhealthy, ERROR_MESSAGE));
  }

  @Test
  void reportsMultipleErrors() {
    String healthy = "healthy";
    String unhealthy1 = "unhealthy1";
    String unhealthy2 = "unhealthy2";
    AccountCredentialsProvider provider =
        stubAccountCredentialsProvider(
            ImmutableList.of(
                healthyAccount(healthy),
                unhealthyAccount(unhealthy1),
                unhealthyAccount(unhealthy2)));

    KubernetesHealthIndicator healthIndicator = new KubernetesHealthIndicator(REGISTRY, provider);

    healthIndicator.checkHealth();
    Health result = healthIndicator.getHealth(true);

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(result.getDetails())
        .containsOnly(entry(unhealthy1, ERROR_MESSAGE), entry(unhealthy2, ERROR_MESSAGE));
    assertThat(result.getDetails())
        .containsOnly(entry(unhealthy1, ERROR_MESSAGE), entry(unhealthy2, ERROR_MESSAGE));
  }

  private static KubernetesConfigurationProperties.ManagedAccount getManagedAccount(String name) {
    KubernetesConfigurationProperties.ManagedAccount managedAccount =
        new KubernetesConfigurationProperties.ManagedAccount();
    managedAccount.setName(name);
    return managedAccount;
  }

  private static KubernetesNamedAccountCredentials<KubernetesCredentials> healthyAccount(
      String name) {
    return new KubernetesNamedAccountCredentials<>(
        getManagedAccount(name), HEALTHY_CREDENTIAL_FACTORY);
  }

  private static KubernetesNamedAccountCredentials<KubernetesCredentials> unhealthyAccount(
      String name) {
    return new KubernetesNamedAccountCredentials<>(
        getManagedAccount(name), UNHEALTHY_CREDENTIAL_FACTORY);
  }

  private static AccountCredentials nonKubernetesAccount(String name) {
    AccountCredentials credentials = mock(AccountCredentials.class);
    when(credentials.getName()).thenReturn(name);
    return credentials;
  }

  private static AccountCredentialsProvider stubAccountCredentialsProvider(
      Iterable<AccountCredentials> accounts) {
    AccountCredentialsRepository accountRepository = new MapBackedAccountCredentialsRepository();
    for (AccountCredentials account : accounts) {
      accountRepository.save(account.getName(), account);
    }
    return new DefaultAccountCredentialsProvider(accountRepository);
  }
}
