/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.kork.github;

import lombok.Value;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

/**
 * Configuration for GitHub App authentication.
 *
 * <p>Intended to be used as a nested configuration model (e.g. a {@code githubApp} block on an
 * artifact account) by Spinnaker components that authenticate against GitHub as a GitHub App.
 *
 * <p>Example YAML:
 *
 * <pre>
 * githubApp:
 *   appId: "123456"
 *   appPrivateKeyPath: /secrets/gh-app-key.pem
 *   appInstallationId: "789012"
 *   # apiBaseUrl: https://ghe.example.com/api/v3  # optional, for GitHub Enterprise
 * </pre>
 */
@Value
public class GitHubAppCredentials {

  public static final String DEFAULT_API_BASE_URL = "https://api.github.com";

  String appId;
  String appPrivateKeyPath;
  String appInstallationId;
  String apiBaseUrl;

  @ConstructorBinding
  public GitHubAppCredentials(
      String appId, String appPrivateKeyPath, String appInstallationId, String apiBaseUrl) {
    if (!StringUtils.hasText(appId)) {
      throw new IllegalArgumentException(
          "githubApp.appId is required when GitHub App authentication is configured.");
    }
    if (!StringUtils.hasText(appPrivateKeyPath)) {
      throw new IllegalArgumentException(
          "githubApp.appPrivateKeyPath is required when GitHub App authentication is configured.");
    }
    if (!StringUtils.hasText(appInstallationId)) {
      throw new IllegalArgumentException(
          "githubApp.appInstallationId is required when GitHub App authentication is configured.");
    }
    this.appId = appId;
    this.appPrivateKeyPath = appPrivateKeyPath;
    this.appInstallationId = appInstallationId;
    this.apiBaseUrl = StringUtils.hasText(apiBaseUrl) ? apiBaseUrl : DEFAULT_API_BASE_URL;
  }

  /**
   * Creates an authenticator that mints and caches installation tokens for this GitHub App.
   *
   * @return a new {@link GitHubAppAuthenticator}
   */
  public GitHubAppAuthenticator toAuthenticator() {
    return new GitHubAppAuthenticator(appId, appPrivateKeyPath, appInstallationId, apiBaseUrl);
  }
}
