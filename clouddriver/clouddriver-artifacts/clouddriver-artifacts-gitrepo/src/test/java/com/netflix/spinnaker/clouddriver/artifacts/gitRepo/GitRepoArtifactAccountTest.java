/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

@ExtendWith({TempDirectory.class})
public class GitRepoArtifactAccountTest {

  @Test
  void shouldGetTokenFromFile(@TempDirectory.TempDir Path tempDir) throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder()
            .name("gitRepo-account")
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    assertThat(account.getTokenAsString().get()).isEqualTo("zzz");
  }

  @Test
  void shouldGetTokenFromProperty() {
    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder().name("gitRepo-account").token("tokentoken").build();

    assertThat(account.getTokenAsString().get()).isEqualTo("tokentoken");
  }

  @Test
  void shouldBindNestedGitHubAppBlock() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "gitRepo-account");
    properties.put("githubApp.appId", "12345");
    properties.put("githubApp.appPrivateKeyPath", "/secrets/gh-app-key.pem");
    properties.put("githubApp.appInstallationId", "67890");
    properties.put("githubApp.apiBaseUrl", "https://ghe.example.com/api/v3");

    GitRepoArtifactAccount account = bind(properties);

    assertThat(account.getGithubApp().isPresent()).isTrue();
    assertThat(account.getGithubApp().get().getAppId()).isEqualTo("12345");
    assertThat(account.getGithubApp().get().getAppPrivateKeyPath())
        .isEqualTo("/secrets/gh-app-key.pem");
    assertThat(account.getGithubApp().get().getAppInstallationId()).isEqualTo("67890");
    assertThat(account.getGithubApp().get().getApiBaseUrl())
        .isEqualTo("https://ghe.example.com/api/v3");
  }

  @Test
  void shouldBindAllowedOrganizations() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "gitRepo-account");
    properties.put("githubApp.appId", "12345");
    properties.put("githubApp.appPrivateKeyPath", "/secrets/gh-app-key.pem");
    properties.put("githubApp.allowedOrganizations[0]", "My-Org");
    properties.put("githubApp.allowedOrganizations[1]", "other-org");

    GitRepoArtifactAccount account = bind(properties);

    assertThat(account.getGithubApp().get().getAllowedOrganizations())
        .isEqualTo(Set.of("my-org", "other-org"));
    assertThat(account.getGithubApp().get().isUnrestrictedDeriveMode()).isFalse();
  }

  @Test
  void shouldAllowAnyOrganizationWhenAllowedOrganizationsIsNotConfigured() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "gitRepo-account");
    properties.put("githubApp.appId", "12345");
    properties.put("githubApp.appPrivateKeyPath", "/secrets/gh-app-key.pem");

    GitRepoArtifactAccount account = bind(properties);

    assertThat(account.getGithubApp().get().isUnrestrictedDeriveMode()).isTrue();
  }

  @Test
  void shouldDefaultGitHubAppApiBaseUrlWhenBinding() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "gitRepo-account");
    properties.put("githubApp.appId", "12345");
    properties.put("githubApp.appPrivateKeyPath", "/secrets/gh-app-key.pem");
    properties.put("githubApp.appInstallationId", "67890");

    GitRepoArtifactAccount account = bind(properties);

    assertThat(account.getGithubApp().get().getApiBaseUrl())
        .isEqualTo(GitHubAppCredentials.DEFAULT_API_BASE_URL);
  }

  @Test
  void shouldRejectIncompleteGitHubAppBlockWhenBinding() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "gitRepo-account");
    properties.put("githubApp.appId", "12345");

    assertThrows(Exception.class, () -> bind(properties));
  }

  @Test
  void shouldHaveNoGitHubAppWhenNotConfigured() {
    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder().name("gitRepo-account").build();

    assertThat(account.getGithubApp().isPresent()).isFalse();
  }

  private GitRepoArtifactAccount bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("", Bindable.of(GitRepoArtifactAccount.class))
        .get();
  }
}
