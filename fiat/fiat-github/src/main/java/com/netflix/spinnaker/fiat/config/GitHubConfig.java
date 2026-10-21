package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.GithubTeamsHealthTrackable;
import com.netflix.spinnaker.fiat.roles.github.GithubTeamsUserRolesProvider;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import com.netflix.spinnaker.kork.github.GitHubClientFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import javax.annotation.Nullable;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Converts the list of GitHub Configuration properties to an authenticated GitHub client.
 *
 * <p>Uses hub4j/github-api library for GitHub interactions, which provides:
 *
 * <ul>
 *   <li>Automatic pagination handling
 *   <li>Built-in rate limit management
 *   <li>Type-safe API with rich domain models
 *   <li>Support for both PAT and GitHub App authentication
 * </ul>
 *
 * <p><b>Token Refresh:</b> For GitHub App authentication, installation tokens expire after 1 hour.
 * This configuration creates a {@link GitHubAppAuthenticator} that caches the installation token
 * and renews it shortly before expiry; the {@link #gitHubClient()} bean asks it for the token on
 * every request.
 */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@Slf4j
public class GitHubConfig {

  @Autowired @Setter private GitHubProperties gitHubProperties;

  private GitHubAppAuthenticator gitHubAppAuthenticator;

  @PostConstruct
  public void validateConfiguration() {
    gitHubProperties.validateAuthConfiguration();

    if (gitHubProperties.shouldUseGitHubApp()) {
      log.info(
          "GitHub App authentication enabled for organization: {} (method: {})",
          gitHubProperties.getOrganization(),
          gitHubProperties.getAuthMethod());
      log.info(
          "Benefits: rate limits that scale with the organization (5000/hour and up per installation, 15000/hour on GitHub Enterprise Cloud), enhanced security, and detailed audit logs");

      // Pre-create authenticator for GitHub App (handles token refresh)
      gitHubAppAuthenticator =
          GitHubClientFactory.createAuthenticator(
              gitHubProperties.getBaseUrl(),
              gitHubProperties.getAppId(),
              gitHubProperties.getPrivateKeyPath(),
              gitHubProperties.getInstallationId());
    } else {
      log.info(
          "Personal Access Token authentication enabled for organization: {} (method: {})",
          gitHubProperties.getOrganization(),
          gitHubProperties.getAuthMethod());
      log.warn(
          "Consider migrating to GitHub App authentication for rate limits that scale with the organization and do not depend on a personal account, and enhanced security");
    }
  }

  /**
   * Creates an authenticated GitHub client using either GitHub App or PAT authentication.
   *
   * <p>For GitHub App authentication, the client asks the {@link GitHubAppAuthenticator} for the
   * installation token on every request. The authenticator caches the token and mints a new one
   * shortly before it expires, so this singleton keeps working past the one-hour token lifetime.
   *
   * <p>For PAT authentication, this creates a client with the configured access token (PATs don't
   * expire unless revoked).
   *
   * @return Authenticated GitHub client from hub4j/github-api library
   * @throws IOException if client creation or authentication fails
   */
  @Bean
  public GitHub gitHubClient() throws IOException {
    if (gitHubProperties.shouldUseGitHubApp()) {
      log.debug(
          "Creating GitHub client with GitHub App (app: {}, installation: {})",
          gitHubProperties.getAppId(),
          gitHubProperties.getInstallationId());
      // The returned client fetches the (cached, auto-renewed) installation token per request
      return gitHubAppAuthenticator.getAuthenticatedClient();
    } else {
      log.debug("Creating GitHub client with Personal Access Token");
      return GitHubClientFactory.createWithPAT(
          gitHubProperties.getBaseUrl(), gitHubProperties.getAccessToken());
    }
  }

  /**
   * Adds the GitHub provider to Fiat's health when {@code health-check-enabled} is true; see {@link
   * GitHubProperties#getHealthCheckEnabled()} for what that does and risks.
   */
  @Bean
  @ConditionalOnProperty(
      value = "auth.group-membership.github.health-check-enabled",
      havingValue = "true")
  public GithubTeamsHealthTrackable githubTeamsHealthTrackable(
      GithubTeamsUserRolesProvider provider) {
    return new GithubTeamsHealthTrackable(provider);
  }

  /**
   * Exposes the GitHub App authenticator that backs the {@link GitHub} bean. The bean already
   * renews its installation token; the roles provider uses this to drop a cached token GitHub
   * rejects. With a personal access token this is null, which Spring registers as no bean, so
   * inject it as optional.
   *
   * @return GitHubAppAuthenticator if using GitHub App auth, null otherwise
   */
  @Bean
  @Nullable
  public GitHubAppAuthenticator getGitHubAppAuthenticator() {
    return gitHubAppAuthenticator;
  }
}
