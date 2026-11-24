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

package com.netflix.spinnaker.clouddriver.cloudrun.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.config.CloudrunConfigurationProperties;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CloudrunNamedAccountCredentialsTest {

  private static CloudrunNamedAccountCredentials.Builder builder() {
    CloudrunNamedAccountCredentials.Builder builder = new CloudrunNamedAccountCredentials.Builder();
    builder.setName("cloudrun-account");
    builder.setProject("target-project");
    return builder;
  }

  @Test
  void usesApplicationDefaultCredentialsWithoutKeyOrServiceAccount() {
    assertThat(builder().resolveCredentials()).isExactlyInstanceOf(CloudrunCredentials.class);
  }

  @Test
  void usesTheJsonKeyWhenOneIsConfigured() {
    CloudrunNamedAccountCredentials.Builder builder = builder();
    builder.setJsonKey("{}");
    builder.setServiceAccountId("deployer");
    builder.setServiceAccountProject("identity-project");

    assertThat(builder.resolveCredentials()).isExactlyInstanceOf(CloudrunJsonCredentials.class);
  }

  @Test
  void impersonatesTheConfiguredServiceAccount() {
    CloudrunNamedAccountCredentials.Builder builder = builder();
    builder.setServiceAccountId("deployer");
    builder.setServiceAccountProject("identity-project");

    CloudrunCredentials credentials = builder.resolveCredentials();

    assertThat(credentials).isInstanceOf(CloudrunImpersonatedServiceAccountCredentials.class);
    assertThat(((CloudrunImpersonatedServiceAccountCredentials) credentials).getTargetPrincipal())
        .isEqualTo("deployer@identity-project.iam.gserviceaccount.com");
  }

  @Test
  void rejectsAServiceAccountIdWithoutItsProject() {
    CloudrunNamedAccountCredentials.Builder builder = builder();
    builder.setServiceAccountId("deployer");

    assertThatThrownBy(builder::resolveCredentials)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cloudrun-account")
        .hasMessageContaining("serviceAccountProject");
  }

  @Test
  void rejectsAServiceAccountProjectWithoutItsId() {
    CloudrunNamedAccountCredentials.Builder builder = builder();
    builder.setServiceAccountProject("identity-project");

    assertThatThrownBy(builder::resolveCredentials)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serviceAccountId");
  }

  @Test
  void impersonatedCredentialsWrapTheSourceCredentials() {
    GoogleCredentials source =
        GoogleCredentials.create(new AccessToken("source-token", null /* expiration */));
    CloudrunImpersonatedServiceAccountCredentials credentials =
        new CloudrunImpersonatedServiceAccountCredentials(
            "target-project", "deployer", "identity-project", () -> source);

    GoogleCredentials result = credentials.getCredentials();

    assertThat(result).isInstanceOf(ImpersonatedCredentials.class);
    ImpersonatedCredentials impersonated = (ImpersonatedCredentials) result;
    assertThat(impersonated.getSourceCredentials()).isSameAs(source);
    assertThat(impersonated.getAccount())
        .isEqualTo("deployer@identity-project.iam.gserviceaccount.com");
  }

  @Test
  void reportsTheTargetWhenSourceCredentialsAreMissing() {
    CloudrunImpersonatedServiceAccountCredentials credentials =
        new CloudrunImpersonatedServiceAccountCredentials(
            "target-project",
            "deployer",
            "identity-project",
            () -> {
              throw new IOException("no application default credentials");
            });

    assertThatThrownBy(credentials::getCredentials)
        .hasMessageContaining("deployer@identity-project.iam.gserviceaccount.com")
        .hasRootCauseMessage("no application default credentials");
  }

  @Test
  void bindsTheServiceAccountSettingsOnAnAccount() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "cloudrun.accounts[0].name", "cloudrun-account",
                    "cloudrun.accounts[0].project", "target-project",
                    "cloudrun.accounts[0].service-account-id", "deployer",
                    "cloudrun.accounts[0].service-account-project", "identity-project")));

    CloudrunConfigurationProperties.ManagedAccount account =
        binder.bind("cloudrun", CloudrunConfigurationProperties.class).get().getAccounts().get(0);

    assertThat(account.getServiceAccountId()).isEqualTo("deployer");
    assertThat(account.getServiceAccountProject()).isEqualTo("identity-project");
  }
}
