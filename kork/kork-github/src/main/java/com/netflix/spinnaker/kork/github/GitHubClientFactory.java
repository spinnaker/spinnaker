/*
 * Copyright 2025 Razorpay.
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

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Factory for creating authenticated GitHub clients.
 *
 * <p>Supports both Personal Access Token (PAT) and GitHub App authentication methods.
 *
 * <p>Example usage with PAT:
 *
 * <pre>{@code
 * GitHub github = GitHubClientFactory.createWithPAT(
 *     "https://api.github.com",
 *     "ghp_xxxxx"
 * );
 * }</pre>
 *
 * <p>Example usage with GitHub App:
 *
 * <pre>{@code
 * GitHub github = GitHubClientFactory.createWithGitHubApp(
 *     "https://api.github.com",
 *     "12345",
 *     "/path/to/private-key.pem",
 *     "67890"
 * );
 * }</pre>
 */
@Slf4j
public class GitHubClientFactory {

  /**
   * Creates a GitHub client using Personal Access Token authentication.
   *
   * @param baseUrl GitHub API base URL (e.g., "https://api.github.com" or GitHub Enterprise URL)
   * @param accessToken Personal Access Token with appropriate permissions
   * @return Authenticated GitHub client
   * @throws IOException if client creation fails
   */
  public static GitHub createWithPAT(String baseUrl, String accessToken) throws IOException {
    log.info("Creating GitHub client with PAT for: {}", baseUrl);
    return new GitHubBuilder().withEndpoint(baseUrl).withOAuthToken(accessToken).build();
  }

  /**
   * Creates a GitHub client using GitHub App authentication.
   *
   * <p>This method internally creates a {@link GitHubAppAuthenticator} to handle JWT generation and
   * installation token management.
   *
   * <p><b>Warning:</b> GitHub App installation tokens expire after 1 hour. The returned client does
   * NOT auto-refresh tokens. For long-running applications, consider using {@link
   * #createAuthenticator(String, String, String, String)} and calling {@link
   * GitHubAppAuthenticator#getAuthenticatedClient()} when needed to ensure fresh tokens.
   *
   * @param baseUrl GitHub API base URL (e.g., "https://api.github.com" or GitHub Enterprise URL)
   * @param appId GitHub App ID
   * @param privateKeyPath Path to the PEM-encoded private key file
   * @param installationId GitHub App installation ID
   * @return Authenticated GitHub client with a token valid at time of creation
   * @throws IOException if client creation or authentication fails
   */
  public static GitHub createWithGitHubApp(
      String baseUrl, String appId, String privateKeyPath, String installationId)
      throws IOException {
    log.info(
        "Creating GitHub client with GitHub App for: {} (app: {}, installation: {})",
        baseUrl,
        appId,
        installationId);

    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(appId, privateKeyPath, installationId, baseUrl);

    return authenticator.getAuthenticatedClient();
  }

  /**
   * Creates a GitHub client using an existing {@link GitHubAppAuthenticator}.
   *
   * <p>Useful when you need to reuse an authenticator across multiple client creations.
   *
   * @param authenticator Pre-configured GitHub App authenticator
   * @return Authenticated GitHub client
   * @throws IOException if client creation fails
   */
  public static GitHub createWithAuthenticator(GitHubAppAuthenticator authenticator)
      throws IOException {
    return authenticator.getAuthenticatedClient();
  }

  /**
   * Creates a GitHub App authenticator for obtaining fresh tokens.
   *
   * <p>This is the recommended approach for long-running applications using GitHub App
   * authentication. The authenticator handles token caching and automatic refresh.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * // Create once at startup
   * GitHubAppAuthenticator authenticator = GitHubClientFactory.createAuthenticator(
   *     "https://api.github.com", "12345", "/path/to/key.pem", "67890");
   *
   * // Get a fresh client when needed (handles token refresh automatically)
   * GitHub client = authenticator.getAuthenticatedClient();
   * }</pre>
   *
   * @param baseUrl GitHub API base URL (e.g., "https://api.github.com" or GitHub Enterprise URL)
   * @param appId GitHub App ID
   * @param privateKeyPath Path to the PEM-encoded private key file
   * @param installationId GitHub App installation ID
   * @return Authenticator that can be used to create fresh GitHub clients
   */
  public static GitHubAppAuthenticator createAuthenticator(
      String baseUrl, String appId, String privateKeyPath, String installationId) {
    log.info(
        "Creating GitHub App authenticator for: {} (app: {}, installation: {})",
        baseUrl,
        appId,
        installationId);

    return new GitHubAppAuthenticator(appId, privateKeyPath, installationId, baseUrl);
  }
}
