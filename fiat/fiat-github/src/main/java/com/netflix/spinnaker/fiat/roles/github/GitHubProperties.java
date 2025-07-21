package com.netflix.spinnaker.fiat.roles.github;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Helper class to map masters in properties file into a validated property map */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@ConfigurationProperties(prefix = "auth.group-membership.github")
@Data
public class GitHubProperties {
  @NotEmpty private String baseUrl;
  @NotEmpty private String organization;

  // Authentication method control
  private AuthMethod authMethod = AuthMethod.AUTO;

  // Personal Access Token authentication (legacy)
  private String accessToken;

  // GitHub App authentication (preferred)
  private String appId;
  private String privateKeyPath;
  private String installationId;

  public enum AuthMethod {
    AUTO, // GitHub App if available, otherwise PAT
    GITHUB_APP, // Force GitHub App (fail if not configured)
    PAT // Force PAT (fail if not configured)
  }

  @NotNull
  @Max(100L)
  @Min(1L)
  Integer paginationValue = 100;

  @NotNull Integer membershipCacheTTLSeconds = 60 * 10; // 10 min time to refresh
  @NotNull Integer membershipCacheTeamsSize = 1000; // 1000 github teams

  /**
   * Determines if GitHub App authentication should be used based on configuration and method
   * preference. Priority: GitHub App > PAT (for better rate limits and security)
   */
  public boolean shouldUseGitHubApp() {
    boolean appConfigured = isGitHubAppConfigured();
    boolean patConfigured = isPATConfigured();

    switch (authMethod) {
      case GITHUB_APP:
        if (!appConfigured) {
          throw new IllegalStateException(
              "GitHub App authentication is forced but not properly configured. "
                  + "Provide 'appId', 'privateKeyPath', and 'installationId'.");
        }
        return true;

      case PAT:
        if (!patConfigured) {
          throw new IllegalStateException(
              "PAT authentication is forced but 'accessToken' is not configured.");
        }
        return false;

      case AUTO:
      default:
        // GitHub App takes precedence for better rate limits and security
        if (appConfigured) {
          return true;
        } else if (patConfigured) {
          return false;
        } else {
          throw new IllegalStateException(
              "No GitHub authentication method configured. "
                  + "Provide either 'accessToken' for PAT or 'appId', 'privateKeyPath', 'installationId' for GitHub App.");
        }
    }
  }

  /** Checks if GitHub App authentication is properly configured. */
  public boolean isGitHubAppConfigured() {
    return StringUtils.hasText(appId)
        && StringUtils.hasText(privateKeyPath)
        && StringUtils.hasText(installationId);
  }

  /** Checks if Personal Access Token authentication is configured. */
  public boolean isPATConfigured() {
    return StringUtils.hasText(accessToken);
  }

  /** Validates authentication configuration based on the selected method. */
  public void validateAuthConfiguration() {
    // This will throw appropriate exceptions if configuration is invalid
    shouldUseGitHubApp();
  }
}
