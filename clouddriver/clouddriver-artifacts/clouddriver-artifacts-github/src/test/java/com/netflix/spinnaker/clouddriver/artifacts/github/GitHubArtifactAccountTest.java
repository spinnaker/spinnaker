/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GitHubArtifactAccountTest {

  @Test
  void shouldBindNestedGitHubAppBlock() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "github-account");
    properties.put("githubApp.appId", "12345");
    properties.put("githubApp.appPrivateKeyPath", "/secrets/gh-app-key.pem");
    properties.put("githubApp.appInstallationId", "67890");
    properties.put("githubApp.apiBaseUrl", "https://ghe.example.com/api/v3");

    GitHubArtifactAccount account = bind(properties);

    assertThat(account.getGithubApp()).isPresent();
    assertThat(account.getGithubApp().get().getAppId()).isEqualTo("12345");
    assertThat(account.getGithubApp().get().getAppPrivateKeyPath())
        .isEqualTo("/secrets/gh-app-key.pem");
    assertThat(account.getGithubApp().get().getAppInstallationId()).isEqualTo("67890");
    assertThat(account.getGithubApp().get().getApiBaseUrl())
        .isEqualTo("https://ghe.example.com/api/v3");
  }

  @Test
  void shouldDefaultGitHubAppApiBaseUrlWhenBinding() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "github-account");
    properties.put("githubApp.appId", "12345");
    properties.put("githubApp.appPrivateKeyPath", "/secrets/gh-app-key.pem");
    properties.put("githubApp.appInstallationId", "67890");

    GitHubArtifactAccount account = bind(properties);

    assertThat(account.getGithubApp().get().getApiBaseUrl())
        .isEqualTo(GitHubAppCredentials.DEFAULT_API_BASE_URL);
  }

  @Test
  void shouldRejectIncompleteGitHubAppBlockWhenBinding() {
    Map<String, String> properties = new HashMap<>();
    properties.put("name", "github-account");
    properties.put("githubApp.appId", "12345");

    assertThrows(Exception.class, () -> bind(properties));
  }

  @Test
  void shouldHaveNoGitHubAppWhenNotConfigured() {
    GitHubArtifactAccount account = GitHubArtifactAccount.builder().name("github-account").build();

    assertThat(account.getGithubApp()).isEmpty();
  }

  private GitHubArtifactAccount bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("", Bindable.of(GitHubArtifactAccount.class))
        .get();
  }
}
