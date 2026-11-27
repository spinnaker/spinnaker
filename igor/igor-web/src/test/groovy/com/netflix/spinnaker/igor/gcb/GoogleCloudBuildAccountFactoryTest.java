/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.igor.gcb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.google.api.services.cloudbuild.v1.CloudBuildScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import com.netflix.spinnaker.kork.google.auth.GoogleAuthProperties;
import com.netflix.spinnaker.kork.google.auth.GoogleCredentialsFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

@ExtendWith(MockitoExtension.class)
class GoogleCloudBuildAccountFactoryTest {
  private final GoogleCredentialsService credentialService = mock(GoogleCredentialsService.class);
  private final GoogleCloudBuildClient.Factory clientFactory =
      mock(GoogleCloudBuildClient.Factory.class);
  private final GoogleCloudBuildClient client = mock(GoogleCloudBuildClient.class);
  private final GoogleCredentials credentials = mock(GoogleCredentials.class);
  private final GoogleCloudBuildAccountFactory factory =
      new GoogleCloudBuildAccountFactory(
          credentialService,
          clientFactory,
          mock(GoogleCloudBuildCache.Factory.class),
          new GoogleCloudBuildParser());

  @Test
  void defaultsToAnEmptyAuthConfiguration() {
    GoogleCloudBuildProperties.Account account =
        GoogleCloudBuildProperties.Account.builder()
            .name("test-account")
            .project("test-project")
            .build();
    when(credentialService.getCredentials(account.getAuth())).thenReturn(credentials);
    when(clientFactory.create(credentials, "test-project")).thenReturn(client);

    factory.build(account);

    assertThat(account.getAuth().getCredentialsFile()).isNull();
    assertThat(account.getAuth().getImpersonateServiceAccount()).isNull();
    verify(clientFactory).create(credentials, "test-project");
  }

  @Test
  void retainsRegionalClientSelection() {
    GoogleCloudBuildProperties.Account account =
        GoogleCloudBuildProperties.Account.builder()
            .name("test-account")
            .project("test-project")
            .cloudBuildRegion("north")
            .build();
    when(credentialService.getCredentials(account.getAuth())).thenReturn(credentials);
    when(clientFactory.create(credentials, "test-project", "north")).thenReturn(client);

    factory.build(account);

    verify(clientFactory).create(credentials, "test-project", "north");
  }

  @Test
  void passesBoundFileAndImpersonationSettingsToTheCredentialService() {
    GoogleCloudBuildProperties properties =
        bind(
            Map.of(
                "gcb.accounts[0].name",
                "test-account",
                "gcb.accounts[0].project",
                "test-project",
                "gcb.accounts[0].auth.credentials-file",
                "credentials.json",
                "gcb.accounts[0].auth.impersonate-service-account",
                "builder@identity-project.iam.gserviceaccount.com"));
    GoogleCloudBuildProperties.Account account = properties.getAccounts().get(0);
    when(credentialService.getCredentials(account.getAuth())).thenReturn(credentials);
    when(clientFactory.create(credentials, "test-project")).thenReturn(client);

    factory.build(account);

    verify(credentialService).getCredentials(account.getAuth());
    assertThat(account.getAuth().getCredentialsFile()).isEqualTo("credentials.json");
    assertThat(account.getAuth().getImpersonateServiceAccount())
        .isEqualTo("builder@identity-project.iam.gserviceaccount.com");
  }

  @Test
  void rejectsTheRemovedJsonKeySetting() {
    assertThatThrownBy(
            () ->
                bind(
                    Map.of(
                        "gcb.accounts[0].name",
                        "test-account",
                        "gcb.accounts[0].project",
                        "test-project",
                        "gcb.accounts[0].json-key",
                        "unused")))
        .hasRootCauseMessage("jsonKey is no longer supported; use auth.credentialsFile");
  }

  @Test
  void credentialServiceAppliesCloudBuildScopes() throws IOException {
    GoogleCredentialsFactory shared = mock(GoogleCredentialsFactory.class);
    GoogleAuthProperties auth = new GoogleAuthProperties();
    when(shared.create(auth, CloudBuildScopes.all())).thenReturn(credentials);

    assertThat(new GoogleCredentialsService(shared).getCredentials(auth)).isSameAs(credentials);
    verify(shared).create(auth, CloudBuildScopes.all());
  }

  @Test
  void credentialServicePropagatesFailure() throws IOException {
    GoogleCredentialsFactory shared = mock(GoogleCredentialsFactory.class);
    GoogleAuthProperties auth = new GoogleAuthProperties();
    when(shared.create(auth, CloudBuildScopes.all()))
        .thenThrow(new IOException("credential failure"));

    assertThatThrownBy(() -> new GoogleCredentialsService(shared).getCredentials(auth))
        .isInstanceOf(UncheckedIOException.class)
        .hasRootCauseMessage("credential failure");
  }

  private static GoogleCloudBuildProperties bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("gcb", GoogleCloudBuildProperties.class)
        .get();
  }
}
