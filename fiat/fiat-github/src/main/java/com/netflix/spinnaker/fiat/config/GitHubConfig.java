package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import com.netflix.spinnaker.kork.github.GitHubClientFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
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
 * This configuration provides a {@link GitHubAppAuthenticator} bean that handles automatic token
 * refresh. The {@link #gitHubClient()} method returns a fresh client with a valid token on each
 * call when using GitHub App auth.
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
          "Benefits: Better rate limits (5000/hour vs 15000/hour), enhanced security, and detailed audit logs");

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
          "Consider migrating to GitHub App authentication for better rate limits (5000/hour vs 15000/hour) and enhanced security");
    }
  }

  /**
   * Creates an authenticated GitHub client using either GitHub App or PAT authentication.
   *
   * <p>For GitHub App authentication, this returns a fresh client with a valid token on each call.
   * The {@link GitHubAppAuthenticator} handles token caching and automatic refresh when tokens are
   * about to expire.
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
      // Use authenticator which handles token refresh
      return gitHubAppAuthenticator.getAuthenticatedClient();
    } else {
      log.debug("Creating GitHub client with Personal Access Token");
      return GitHubClientFactory.createWithPAT(
          gitHubProperties.getBaseUrl(), gitHubProperties.getAccessToken());
    }
  }

  /**
   * Provides the GitHub App authenticator for components that need to manage token refresh
   * manually.
   *
   * <p>This is useful for components that hold onto the GitHub client for extended periods and need
   * to refresh it periodically.
   *
   * @return GitHubAppAuthenticator if using GitHub App auth, null otherwise
   */
  public GitHubAppAuthenticator getGitHubAppAuthenticator() {
    return gitHubAppAuthenticator;
  }
}
