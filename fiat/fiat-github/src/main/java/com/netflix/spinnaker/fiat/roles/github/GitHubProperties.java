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

  /**
   * Personal Access Token authentication (legacy). Not required if GitHub App authentication is
   * configured. Validation is performed in validateAuthConfiguration() to allow for flexible auth
   * configuration.
   */
  private String accessToken;

  // GitHub App authentication (preferred)
  private String appId;
  private String privateKeyPath;
  // TODO: make installationId optional by resolving the installation from the organization
  // (GHApp#getInstallationByOrganization), so that reinstalling the app, which gives it a new
  // installation ID, does not need a configuration change.
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
   * Oldest cached GitHub data that roles may still be resolved from, while refreshes keep failing.
   * Past it, role resolution fails: the role sync aborts, leaving the permissions already stored
   * unchanged, and resolving a user who is not stored (e.g. at login) fails. It does not remove
   * stored permissions. 0 disables the limit, so cached data is served for as long as GitHub fails.
   */
  @NotNull
  @Min(0L)
  Integer maxStaleRolesSeconds = 24 * 60 * 60;

  /**
   * Longest a single GitHub fetch (org members, org teams or one team's members) may take. A fetch
   * that takes longer fails, so a hung connection cannot stall cache refreshes indefinitely.
   */
  @NotNull
  @Min(1L)
  Integer refreshTimeoutSeconds = 120;

  /**
   * Whether GitHub counts towards Fiat's health ({@code /health}). Off by default: Fiat reports
   * DOWN until every tracked provider has succeeded once, so a replica starting while GitHub is
   * unreachable would stay DOWN, and with readiness on {@code /health} that can take Fiat down. The
   * {@code fiat.github.*} metrics are published either way, for alerting without that risk.
   */
  @NotNull Boolean healthCheckEnabled = false;

  /**
   * Age of the last successful GitHub fetch after which this provider reports unhealthy, when
   * {@link #healthCheckEnabled}. Defaults to three refresh intervals ({@code 3 *
   * membershipCacheTTLSeconds}).
   */
  @Min(1L)
  Integer alertStalenessSeconds;

  /**
   * How often every Fiat replica fetches the organization's members when {@link
   * #healthCheckEnabled}, so that each replica's health reflects GitHub, not only the replica
   * running the role sync. Costs one GitHub call per 100 members per replica per interval. 0
   * disables it.
   */
  @NotNull
  @Min(0L)
  Integer healthWarmIntervalSeconds = 10 * 60;

  /** Returns {@link #alertStalenessSeconds}, or its default of three refresh intervals. */
  public int getEffectiveAlertStalenessSeconds() {
    return alertStalenessSeconds != null ? alertStalenessSeconds : 3 * membershipCacheTTLSeconds;
  }

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
