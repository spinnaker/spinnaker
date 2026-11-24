/*
 * Copyright 2026 Red Bull Media House GmbH
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for GitHub commit status notifications.
 *
 * <p>Authenticate with exactly one of a personal access token or a GitHub App:
 *
 * <pre>
 * github-status:
 *   enabled: true
 *   endpoint: https://api.github.com  # optional; the GitHub Enterprise API URL otherwise
 *   token: ghp_...                     # either a personal access token
 *   github-app:                        # or a GitHub App
 *     app-id: "123456"
 *     app-private-key-path: /secrets/gh-app-key.pem
 *     app-installation-id: "789012"    # optional - derived from the repository when omitted
 *     allowed-organizations:           # optional - restricts derived installations
 *       - my-org
 *     # api-base-url defaults to github-status.endpoint
 * </pre>
 */
@Data
@ConfigurationProperties("github-status")
public class GithubStatusProperties {

  private boolean enabled;

  /** GitHub API base URL. */
  private String endpoint = GitHubAppCredentials.DEFAULT_API_BASE_URL;

  /** Personal access token. Mutually exclusive with {@link #githubApp}. */
  @ToString.Exclude @Nullable private String token;

  /** GitHub App credentials. Mutually exclusive with {@link #token}. */
  @Nullable private GitHubAppCredentials githubApp;
}
