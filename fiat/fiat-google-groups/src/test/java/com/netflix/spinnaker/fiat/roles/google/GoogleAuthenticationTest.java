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
package com.netflix.spinnaker.fiat.roles.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.google.api.services.cloudidentity.v1.CloudIdentityScopes;
import com.google.api.services.directory.DirectoryScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
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
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void scopesAdcForTheConfiguredGroupLookupWithoutDelegation(boolean transitive) {
    GoogleDirectoryUserRolesProvider.Config config = new GoogleDirectoryUserRolesProvider.Config();
    config.setExpandIndirectGroups(transitive);
    GoogleCredentials source = mock(GoogleCredentials.class);
    GoogleCredentials scoped = mock(GoogleCredentials.class);
    List<String> scopes =
        transitive
            ? List.of(
                DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY,
                CloudIdentityScopes.CLOUD_IDENTITY_GROUPS_READONLY)
            : List.of(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY);
    when(source.createScoped(scopes)).thenReturn(scoped);
    GoogleDirectoryUserRolesProvider provider = provider(config, source);

    assertThat(provider.getGoogleCredential()).isSameAs(scoped);
    assertThat(provider.getGoogleCredential()).isSameAs(scoped);
    verify(source).createScoped(scopes);
    verify(scoped, never()).createDelegated(any());
  }

  @Test
  void boundImpersonationReachesTheSharedResolver() {
    GoogleDirectoryUserRolesProvider.Config config =
        bind(
            Map.of(
                "domain", "example.com",
                "auth.impersonate-service-account",
                    "reader@identity-project.iam.gserviceaccount.com"));
    GoogleCredentials source = mock(GoogleCredentials.class);
    when(source.createScoped(anyCollection())).thenReturn(source);

    GoogleCredentials result = provider(config, source).getGoogleCredential();
    assertThat(result).isInstanceOf(ImpersonatedCredentials.class);
    assertThat(((ImpersonatedCredentials) result).getAccount())
        .isEqualTo("reader@identity-project.iam.gserviceaccount.com");
    assertThat(((ImpersonatedCredentials) result).toBuilder().getScopes())
        .containsExactly(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY);
  }

  @Test
  void explicitFileWithoutAdminUsernameIsNotIgnored() {
    GoogleDirectoryUserRolesProvider.Config config =
        bind(Map.of("auth.credentials-file", "missing.json"));
    GoogleDirectoryUserRolesProvider provider = new GoogleDirectoryUserRolesProvider();
    provider.setConfig(config);
    provider.setCredentialsFactory(
        new GoogleCredentialsFactory(
            () -> {
              throw new AssertionError("file configuration must not use ADC");
            },
            path -> {
              throw new IOException("file unavailable");
            }));

    assertThatThrownBy(provider::getGoogleCredential)
        .isInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("file unavailable");
  }

  @ParameterizedTest
  @ValueSource(strings = {"credential-path", "admin-username"})
  void rejectsRemovedConfigurationDuringBinding(String property) {
    assertThatThrownBy(() -> bind(Map.of(property, "unused")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  private static GoogleDirectoryUserRolesProvider provider(
      GoogleDirectoryUserRolesProvider.Config config, GoogleCredentials source) {
    GoogleDirectoryUserRolesProvider provider = new GoogleDirectoryUserRolesProvider();
    provider.setConfig(config);
    provider.setCredentialsFactory(
        new GoogleCredentialsFactory(
            () -> source,
            path -> {
              throw new AssertionError("unexpected credential file");
            }));
    return provider;
  }

  private static GoogleDirectoryUserRolesProvider.Config bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("", GoogleDirectoryUserRolesProvider.Config.class)
        .get();
  }
}
