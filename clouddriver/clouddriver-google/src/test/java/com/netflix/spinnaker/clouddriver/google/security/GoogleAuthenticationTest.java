/*
 * Copyright 2026 spinnaker.io
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
package com.netflix.spinnaker.clouddriver.google.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpRequest;
import com.google.api.services.compute.ComputeScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.ComputeVersion;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.description.snapshot.RestoreSnapshotDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.snapshot.RestoreSnapshotAtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.kork.google.auth.GoogleAuthProperties;
import com.netflix.spinnaker.kork.google.auth.GoogleCredentialsFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GoogleAuthenticationTest {
  @Test
  void boundAuthReachesTheActualComputeClient() throws IOException {
    GoogleConfigurationProperties.ManagedAccount account =
        bind(
            Map.of(
                "name",
                "compute",
                "project",
                "workload-project",
                "auth.credentials-file",
                "credentials.json",
                "auth.impersonate-service-account",
                "deployer@identity-project.iam.gserviceaccount.com"));
    GoogleCredentialsFactory factory = mock(GoogleCredentialsFactory.class);
    when(factory.create(account.getAuth(), List.of(ComputeScopes.COMPUTE)))
        .thenReturn(GoogleCredentials.create(new AccessToken("test-token", null)));

    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name(account.getName())
            .project(account.getProject())
            .auth(account.getAuth())
            .credentialsFactory(factory)
            .applicationName("test-client")
            .liveLookupsEnabled(false)
            .build();
    HttpRequest request =
        credentials.getCompute().projects().get("workload-project").buildHttpRequest();

    verify(factory).create(account.getAuth(), List.of(ComputeScopes.COMPUTE));
    assertThat(request.getHeaders().getAuthorization()).isEqualTo("Bearer test-token");
    assertThat(account.getAuth().getCredentialsFile()).isEqualTo("credentials.json");
    assertThat(account.getAuth().getImpersonateServiceAccount())
        .isEqualTo("deployer@identity-project.iam.gserviceaccount.com");
  }

  @Test
  void credentialFailureDoesNotBuildAClientWithAnotherIdentity() throws IOException {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setCredentialsFile("missing.json");
    GoogleCredentialsFactory factory = mock(GoogleCredentialsFactory.class);
    when(factory.create(auth, List.of(ComputeScopes.COMPUTE)))
        .thenThrow(new IOException("credential failure"));

    assertThatThrownBy(
            () ->
                new GoogleNamedAccountCredentials.Builder()
                    .name("compute")
                    .project("workload-project")
                    .auth(auth)
                    .credentialsFactory(factory)
                    .liveLookupsEnabled(false)
                    .build())
        .isInstanceOf(IOException.class);
    verify(factory).create(auth, List.of(ComputeScopes.COMPUTE));
  }

  @ParameterizedTest
  @ValueSource(strings = {"json-path", "service-account-id", "service-account-project"})
  void rejectsLegacySpringConfiguration(String property) {
    assertThatThrownBy(() -> bind(Map.of(property, "unused")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"jsonPath", "serviceAccountId", "serviceAccountProject"})
  void rejectsLegacyDynamicAccountDefinitions(String property) {
    ObjectMapper mapper = new ObjectMapper();
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"" + property + "\":\"unused\"}",
                    GoogleConfigurationProperties.ManagedAccount.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dynamicAccountRoundTripDoesNotSerializeRemovedProperties() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    GoogleConfigurationProperties.ManagedAccount account =
        bind(
            Map.of(
                "name",
                "compute",
                "auth.impersonate-service-account",
                "deployer@identity-project.iam.gserviceaccount.com"));
    String json = mapper.writeValueAsString(account);
    assertThat(json).doesNotContain("jsonPath", "serviceAccountId", "serviceAccountProject");
    GoogleConfigurationProperties.ManagedAccount restored =
        mapper.readValue(json, GoogleConfigurationProperties.ManagedAccount.class);
    assertThat(restored.getAuth().getImpersonateServiceAccount())
        .isEqualTo(account.getAuth().getImpersonateServiceAccount());
  }

  @Test
  void snapshotRestoreRejectsImpersonationBeforeConsideringAnotherIdentity() {
    GoogleAuthProperties auth =
        GoogleAuthProperties.builder()
            .impersonateServiceAccount("deployer@identity-project.iam.gserviceaccount.com")
            .build();
    GoogleAuthCredentials runtimeCredentials =
        new GoogleAuthCredentials(
            "workload-project", ComputeVersion.DEFAULT, auth, new GoogleCredentialsFactory());
    GoogleNamedAccountCredentials account = mock(GoogleNamedAccountCredentials.class);
    when(account.getCredentials()).thenReturn(runtimeCredentials);
    when(account.getProject()).thenReturn("workload-project");
    AccountCredentialsRepository repository = mock(AccountCredentialsRepository.class);
    when(repository.getOne("compute")).thenReturn(account);
    RestoreSnapshotDescription description = new RestoreSnapshotDescription();
    description.setAccountName("compute");
    description.setApplicationName("application");
    description.setSnapshotTimestamp(1L);
    RestoreSnapshotAtomicOperation operation = new RestoreSnapshotAtomicOperation(description);
    operation.setAccountCredentialsRepository(repository);

    // No static configuration is provided: this also covers dynamically loaded accounts.
    assertThatThrownBy(() -> operation.operate(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth.impersonateServiceAccount");
  }

  private static GoogleConfigurationProperties.ManagedAccount bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("", GoogleConfigurationProperties.ManagedAccount.class)
        .get();
  }
}
