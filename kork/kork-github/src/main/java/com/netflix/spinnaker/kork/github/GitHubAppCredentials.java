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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
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
 *   appInstallationId: "789012"  # optional - when omitted, the installation is derived from
 *                                # the repository being accessed
 *   allowedOrganizations:        # optional - restricts which repository owners may be accessed
 *     - my-org                   # when the installation is derived
 *   # apiBaseUrl: https://ghe.example.com/api/v3  # optional, for GitHub Enterprise
 * </pre>
 */
@Value
@Slf4j
public class GitHubAppCredentials {

  public static final String DEFAULT_API_BASE_URL = "https://api.github.com";

  private static final Pattern NUMERIC_ID = Pattern.compile("\\d+");

  String appId;
  String appPrivateKeyPath;

  /** Optional. When absent, the installation is derived from the repository being accessed. */
  @Nullable String appInstallationId;

  String apiBaseUrl;

  /**
   * Repository owners (organizations or user accounts) this app may mint installation tokens for
   * when the installation is derived from the repository. Empty means any owner where the app is
   * installed. Ignored when {@code appInstallationId} pins a single installation.
   */
  Set<String> allowedOrganizations;

  public GitHubAppCredentials(
      String appId, String appPrivateKeyPath, String appInstallationId, String apiBaseUrl) {
    this(appId, appPrivateKeyPath, appInstallationId, apiBaseUrl, null);
  }

  @ConstructorBinding
  public GitHubAppCredentials(
      String appId,
      String appPrivateKeyPath,
      String appInstallationId,
      String apiBaseUrl,
      List<String> allowedOrganizations) {
    if (!StringUtils.hasText(appId)) {
      throw new IllegalArgumentException(
          "githubApp.appId is required when GitHub App authentication is configured.");
    }
    if (!StringUtils.hasText(appPrivateKeyPath)) {
      throw new IllegalArgumentException(
          "githubApp.appPrivateKeyPath is required when GitHub App authentication is configured.");
    }
    String normalizedInstallationId =
        StringUtils.hasText(appInstallationId) ? appInstallationId.trim() : null;
    if (normalizedInstallationId != null
        && !NUMERIC_ID.matcher(normalizedInstallationId).matches()) {
      throw new IllegalArgumentException(
          "githubApp.appInstallationId must be numeric, got '" + normalizedInstallationId + "'.");
    }
    this.appId = appId;
    this.appPrivateKeyPath = appPrivateKeyPath;
    this.appInstallationId = normalizedInstallationId;
    this.apiBaseUrl = StringUtils.hasText(apiBaseUrl) ? apiBaseUrl : DEFAULT_API_BASE_URL;
    // GitHub logins are case-insensitive
    this.allowedOrganizations =
        allowedOrganizations == null
            ? Set.of()
            : allowedOrganizations.stream()
                .filter(StringUtils::hasText)
                .map(organization -> organization.trim().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * @return true when a fixed installation is configured (vs. derived per repository owner)
   */
  public boolean hasInstallationId() {
    return appInstallationId != null;
  }

  /**
   * @return true when the installation is derived per repository and no allowlist restricts which
   *     owners may be accessed, i.e. this configuration can reach every organization where the app
   *     is installed. Always false in pinned mode, where the single configured installation is the
   *     restriction.
   */
  public boolean isUnrestrictedDeriveMode() {
    return !hasInstallationId() && allowedOrganizations.isEmpty();
  }

  /**
   * Creates an authenticator that mints and caches installation tokens for this GitHub App, warning
   * when the configuration places no restriction on which organizations can be reached.
   *
   * @param accountDescription how to refer to the owning account in logs and errors, e.g. "git/repo
   *     account 'my-account'"
   * @return a new {@link GitHubAppAuthenticator}
   * @throws IOException if the app private key cannot be loaded
   */
  public GitHubAppAuthenticator toAuthenticator(String accountDescription) throws IOException {
    if (isUnrestrictedDeriveMode()) {
      log.warn(
          "{} uses GitHub App authentication without githubApp.appInstallationId and without"
              + " githubApp.allowedOrganizations, so it can access any organization where the app"
              + " is installed. Set githubApp.allowedOrganizations to restrict it.",
          accountDescription);
    }
    try {
      return new GitHubAppAuthenticator(
          appId, appPrivateKeyPath, appInstallationId, apiBaseUrl, allowedOrganizations);
    } catch (RuntimeException e) {
      throw new IOException(
          "Failed to initialize GitHub App authentication for "
              + accountDescription
              + ": "
              + e.getMessage(),
          e);
    }
  }
}
