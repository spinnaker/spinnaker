package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.kork.github.GitHubClientFactory;
import java.io.IOException;
import javax.annotation.PostConstruct;
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
 */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@Slf4j
public class GitHubConfig {

  @Autowired @Setter private GitHubProperties gitHubProperties;

  @PostConstruct
  public void validateConfiguration() {
    gitHubProperties.validateAuthConfiguration();

    if (gitHubProperties.shouldUseGitHubApp()) {
      log.info(
          "GitHub App authentication enabled for organization: {} (method: {})",
          gitHubProperties.getOrganization(),
          gitHubProperties.getAuthMethod());
      log.info(
          "Benefits: Better rate limits (5000/hour vs 1000/hour), enhanced security, and detailed audit logs");
    } else {
      log.info(
          "Personal Access Token authentication enabled for organization: {} (method: {})",
          gitHubProperties.getOrganization(),
          gitHubProperties.getAuthMethod());
      log.warn(
          "Consider migrating to GitHub App authentication for better rate limits (5000/hour vs 1000/hour) and enhanced security");
    }
  }

  /**
   * Creates an authenticated GitHub client using either GitHub App or PAT authentication.
   *
   * <p>The client is created via {@link GitHubClientFactory} which abstracts the authentication
   * details and provides a unified interface for both authentication methods.
   *
   * @return Authenticated GitHub client from hub4j/github-api library
   * @throws IOException if client creation or authentication fails
   */
  @Bean
  public GitHub gitHubClient() throws IOException {
    if (gitHubProperties.shouldUseGitHubApp()) {
      log.info(
          "Creating GitHub client with GitHub App (app: {}, installation: {})",
          gitHubProperties.getAppId(),
          gitHubProperties.getInstallationId());
      return GitHubClientFactory.createWithGitHubApp(
          gitHubProperties.getBaseUrl(),
          gitHubProperties.getAppId(),
          gitHubProperties.getPrivateKeyPath(),
          gitHubProperties.getInstallationId());
    } else {
      log.info("Creating GitHub client with Personal Access Token");
      return GitHubClientFactory.createWithPAT(
          gitHubProperties.getBaseUrl(), gitHubProperties.getAccessToken());
    }
  }
}
