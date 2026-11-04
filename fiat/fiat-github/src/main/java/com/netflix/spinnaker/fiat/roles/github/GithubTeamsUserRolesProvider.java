/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.roles.github;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * User roles provider that fetches team memberships from GitHub.
 *
 * <p>This provider authenticates users based on their GitHub organization and team memberships.
 * Users must be active members of the configured organization to receive any roles.
 *
 * <p>Uses hub4j/github-api library which provides:
 *
 * <ul>
 *   <li>Automatic pagination of results
 *   <li>Built-in rate limit handling
 *   <li>Type-safe domain models (GHOrganization, GHTeam, GHUser)
 *   <li>Better error messages and exception handling
 * </ul>
 *
 * <p><b>Caching Strategy:</b>
 *
 * <ul>
 *   <li>Organization members: Cached with configurable TTL (default: 10 minutes)
 *   <li>Organization teams: Cached with configurable TTL (default: 10 minutes)
 *   <li>Team memberships: Cached per team with configurable TTL and size limits
 *   <li>Cache refresh happens asynchronously in background to minimize latency
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>A 404 for a team is cached as no members. A 404 for the organization fails like any other
 *       error, because GitHub also answers 404 when the credentials cannot see the organization
 *   <li>Every other error fails the fetch with a {@link ProviderException}. A failed background
 *       refresh keeps the previously cached value and is retried after the next refresh interval; a
 *       failed load with nothing cached surfaces to the caller
 *   <li>A fetch taking longer than {@code refreshTimeoutSeconds} fails the same way
 *   <li>Cached data GitHub last confirmed more than {@code maxStaleRolesSeconds} ago is refused, so
 *       role resolution fails rather than serving roles indefinitely from a dead credential
 *   <li>Rate limit errors provide actionable guidance and rate limit URL
 *   <li>Authentication errors (401) suggest credential verification, and drop the cached GitHub App
 *       installation token so the next request mints a new one
 *   <li>Permission errors (403) suggest permission and rate limit checks
 *   <li>Rate limit status is tracked and warnings are logged when quota is low
 * </ul>
 *
 * @see GitHubProperties for configuration options
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
public class GithubTeamsUserRolesProvider
    implements UserRolesProvider, InitializingBean, DisposableBean {

  @Autowired @Setter private GitHub gitHubClient;

  @Autowired @Setter private GitHubProperties gitHubProperties;

  /**
   * The authenticator behind {@link #gitHubClient} when GitHub App authentication is used, so that
   * a token GitHub rejects can be dropped; null with a personal access token.
   */
  @Autowired(required = false)
  @Setter
  @Nullable
  private GitHubAppAuthenticator gitHubAppAuthenticator;

  @Autowired @Setter private MeterRegistry meterRegistry;

  @Setter(AccessLevel.PACKAGE)
  private ExecutorService executor =
      Executors.newSingleThreadExecutor(daemonThreads("github-roles-refresh-%d"));

  /**
   * Runs each GitHub fetch, so that a fetch exceeding {@code refreshTimeoutSeconds} can be
   * abandoned while the caller moves on.
   */
  private final ExecutorService fetchExecutor =
      Executors.newCachedThreadPool(daemonThreads("github-roles-fetch-%d"));

  /** Fetches the org members on every replica; null unless health checks are enabled. */
  @Nullable private ScheduledExecutorService warmScheduler;

  /**
   * Clock for cache refresh intervals; tests replace it so they can advance time. Read when the
   * caches are built in {@link #afterPropertiesSet()}, so set it before that.
   */
  @Setter(AccessLevel.PACKAGE)
  private Ticker ticker = Ticker.systemTicker();

  private LoadingCache<String, Fetched<Set<String>>> membersCache;

  private LoadingCache<String, Fetched<Map<String, GHTeam>>> teamsCache;

  private LoadingCache<String, Fetched<Set<String>>> teamMembershipCache;

  private ProviderHealthTracker healthTracker;

  /** Remaining core API rate limit from the latest check, or -1 before the first check. */
  private final AtomicLong rateLimitRemaining = new AtomicLong(-1);

  /**
   * A cached GitHub result with the {@link #ticker} time it was fetched, and when refreshing it
   * started failing (null while refreshes succeed). A failed refresh keeps the value and its fetch
   * time and records the first failure, so staleness measures how long GitHub has been failing, not
   * how long the entry went unread: refreshes only start when an entry is read.
   */
  private record Fetched<V>(V value, long fetchedAtNanos, @Nullable Long failingSinceNanos) {

    Fetched<V> failingSince(long nanos) {
      return failingSinceNanos != null ? this : new Fetched<>(value, fetchedAtNanos, nanos);
    }
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(gitHubProperties.getOrganization() != null, "Supply an organization");
    Assert.state(gitHubProperties.getBaseUrl() != null, "Supply a base url");
    // GitHubProperties is not @Validated, so its @Min constraints are not enforced
    Assert.state(
        gitHubProperties.getRefreshTimeoutSeconds() > 0, "refreshTimeoutSeconds must be positive");
    Assert.state(
        gitHubProperties.getMaxStaleRolesSeconds() >= 0,
        "maxStaleRolesSeconds must be 0 (disabled) or positive");
    Assert.state(
        gitHubProperties.getAlertStalenessSeconds() == null
            || gitHubProperties.getAlertStalenessSeconds() > 0,
        "alertStalenessSeconds must be positive");
    Assert.state(
        gitHubProperties.getHealthWarmIntervalSeconds() >= 0,
        "healthWarmIntervalSeconds must be 0 (disabled) or positive");
    Assert.state(
        !gitHubProperties.getHealthCheckEnabled()
            || gitHubProperties.getHealthWarmIntervalSeconds() == 0
            || gitHubProperties.getEffectiveAlertStalenessSeconds()
                > gitHubProperties.getHealthWarmIntervalSeconds(),
        "alertStalenessSeconds must be longer than healthWarmIntervalSeconds, or replicas not"
            + " running the role sync report unhealthy between warms");

    this.healthTracker =
        new ProviderHealthTracker(
            TimeUnit.SECONDS.toMillis(gitHubProperties.getEffectiveAlertStalenessSeconds()));
    this.initializeMembersCache();
    this.initializeTeamsCache();
    this.initializeTeamMembershipCache();
    this.registerGauges();
    this.scheduleOrgMembersWarming();
  }

  @Override
  public void destroy() {
    if (warmScheduler != null) {
      warmScheduler.shutdownNow();
    }
    executor.shutdownNow();
    fetchExecutor.shutdownNow();
  }

  /**
   * Healthy while a GitHub fetch has succeeded within {@code alertStalenessSeconds}. It counts
   * towards Fiat's health only through {@link GithubTeamsHealthTrackable}, when {@code
   * healthCheckEnabled}. Only the replica holding the role-sync lock resolves every user's roles,
   * so with health checks on every replica also fetches the org members on a schedule (see {@link
   * #warmOrgMembers()}); otherwise the other replicas would never fetch, and never report healthy.
   */
  public ProviderHealthTracker getHealthTracker() {
    return healthTracker;
  }

  private void registerGauges() {
    meterRegistry.gauge("fiat.github.rateLimitRemaining", rateLimitRemaining);
    Gauge.builder(
            "fiat.github.dataAgeSeconds", this, GithubTeamsUserRolesProvider::orgMembersAgeSeconds)
        .description(
            "Seconds since this replica last fetched the GitHub org members; alert on this rather"
                + " than on /health, which stays UP after the first success")
        .register(meterRegistry);
    if (gitHubAppAuthenticator != null) {
      Gauge.builder(
              "fiat.github.tokenSecondsToExpiry",
              gitHubAppAuthenticator,
              GithubTeamsUserRolesProvider::secondsToTokenExpiry)
          .description("Seconds until the cached GitHub App installation token expires")
          .register(meterRegistry);
    }
  }

  /** Age of the cached org members, without loading them; NaN if nothing is cached. */
  private double orgMembersAgeSeconds() {
    Fetched<Set<String>> members = membersCache.getIfPresent(gitHubProperties.getOrganization());
    return members == null
        ? Double.NaN
        : TimeUnit.NANOSECONDS.toSeconds(ticker.read() - members.fetchedAtNanos());
  }

  private static double secondsToTokenExpiry(GitHubAppAuthenticator authenticator) {
    Instant expiry = authenticator.getInstallationTokenExpiry();
    return expiry == null ? Double.NaN : Duration.between(Instant.now(), expiry).toSeconds();
  }

  private void scheduleOrgMembersWarming() {
    int intervalSeconds = gitHubProperties.getHealthWarmIntervalSeconds();
    if (!gitHubProperties.getHealthCheckEnabled() || intervalSeconds <= 0) {
      return;
    }
    warmScheduler =
        Executors.newSingleThreadScheduledExecutor(daemonThreads("github-roles-warm-%d"));
    warmScheduler.scheduleWithFixedDelay(
        this::warmOrgMembers, 0, intervalSeconds, TimeUnit.SECONDS);
  }

  /**
   * Fetches the org members again. This is the cheapest fetch that proves the credentials and
   * GitHub work (one call per 100 members), so it keeps this replica's health current and its
   * members cache warm for logins. Teams are fetched only when roles are resolved.
   */
  void warmOrgMembers() {
    String orgName = gitHubProperties.getOrganization();
    // Skip members fetched successfully within the interval, e.g. on the replica running the sync
    Fetched<Set<String>> members = membersCache.getIfPresent(orgName);
    long intervalNanos = TimeUnit.SECONDS.toNanos(gitHubProperties.getHealthWarmIntervalSeconds());
    if (members != null
        && members.failingSinceNanos() == null
        && ticker.read() - members.fetchedAtNanos() < intervalNanos) {
      return;
    }
    try {
      // A failed fetch keeps any cached value; fetchFailure has already logged it
      membersCache.refresh(orgName);
    } catch (RuntimeException e) {
      // An exception escaping a scheduleWithFixedDelay task would cancel all later warms
      log.warn("Failed to warm the GitHub org members cache", e);
    }
  }

  private static ThreadFactory daemonThreads(String nameFormat) {
    return new ThreadFactoryBuilder().setDaemon(true).setNameFormat(nameFormat).build();
  }

  /**
   * Initializes the organization members cache.
   *
   * <p>Caches the set of all active members in the organization. This cache is refreshed
   * asynchronously in the background after the TTL expires, ensuring low-latency responses even
   * during cache refresh.
   *
   * <p><b>Cache Size:</b> Limited to 1 entry (single organization support). If multi-org support is
   * added in the future, this size limit must be increased.
   */
  private void initializeMembersCache() {
    this.membersCache =
        CacheBuilder.newBuilder()
            .maximumSize(1) // Single org support - increase if multi-org is needed
            .refreshAfterWrite(
                this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
            .ticker(ticker)
            .build(refreshingLoader(FetchOperation.MEMBERS, this::fetchOrgMembers));
  }

  /**
   * Initializes the organization teams cache.
   *
   * <p>Caches all teams in the organization as a map (team slug -> GHTeam). This enables fast
   * lookups when checking team memberships for users. Refresh happens asynchronously.
   *
   * <p><b>Cache Size:</b> Limited to 1 entry (single organization support). If multi-org support is
   * added in the future, this size limit must be increased.
   */
  private void initializeTeamsCache() {
    this.teamsCache =
        CacheBuilder.newBuilder()
            .maximumSize(1) // Single org support - increase if multi-org is needed
            .refreshAfterWrite(
                this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
            .ticker(ticker)
            .build(refreshingLoader(FetchOperation.TEAMS, this::fetchOrgTeams));
  }

  /**
   * Initializes the team membership cache.
   *
   * <p>Caches the members of each team individually (keyed by team slug). This is more
   * memory-efficient than caching all team memberships upfront, especially for organizations with
   * many teams.
   *
   * <p><b>Cache Size:</b> Configurable per deployment. Default allows caching multiple teams based
   * on usage patterns. LRU eviction ensures most-accessed teams stay cached.
   */
  private void initializeTeamMembershipCache() {
    this.teamMembershipCache =
        CacheBuilder.newBuilder()
            .maximumSize(this.gitHubProperties.getMembershipCacheTeamsSize())
            .refreshAfterWrite(
                this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
            .ticker(ticker)
            .build(refreshingLoader(FetchOperation.TEAM_MEMBERS, this::fetchTeamMembers));
  }

  /**
   * Returns a cache loader that fetches with {@code fetch} and refreshes on {@link #executor}.
   *
   * <p>A failed refresh returns the previous value instead of failing. Guava stores it as a fresh
   * write, so the cache keeps serving it and the next attempt waits a full refresh interval. If the
   * refresh failed instead, the write time would stay unchanged and every read would start another
   * GitHub call for as long as GitHub keeps failing. A load with nothing cached still throws. The
   * previous value keeps its fetch time, so {@link #getCached} can refuse it once it is too old.
   *
   * @param operation the data fetched, for logs and metrics
   */
  private <V> CacheLoader<String, Fetched<V>> refreshingLoader(
      FetchOperation operation, Function<String, V> fetch) {
    return new CacheLoader<String, Fetched<V>>() {
      @Override
      public Fetched<V> load(String key) {
        V value = fetchWithTimeout(operation, fetch, key);
        healthTracker.success();
        return new Fetched<>(value, ticker.read(), null);
      }

      @Override
      public ListenableFuture<Fetched<V>> reload(String key, Fetched<V> prev) {
        ListenableFutureTask<Fetched<V>> task =
            ListenableFutureTask.create(
                () -> {
                  try {
                    return load(key);
                  } catch (ProviderException e) {
                    // fetchFailure has already logged the error
                    return prev.failingSince(ticker.read());
                  } catch (RuntimeException e) {
                    log.error(
                        "Unexpected error refreshing GitHub cache entry {}; keeping previous value",
                        key,
                        e);
                    return prev.failingSince(ticker.read());
                  }
                });
        executor.execute(task);
        return task;
      }
    };
  }

  /**
   * Runs {@code fetch} on {@link #fetchExecutor}, giving up after {@code refreshTimeoutSeconds}.
   * hub4j's default HTTP connector sets no read timeout, so without this one hung request would
   * stall the single refresh thread, and with it every later refresh, while the caches kept serving
   * ever older data.
   */
  private <V> V fetchWithTimeout(FetchOperation operation, Function<String, V> fetch, String key) {
    int timeoutSeconds = gitHubProperties.getRefreshTimeoutSeconds();
    Future<V> future = fetchExecutor.submit(() -> fetch.apply(key));
    try {
      return future.get(timeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw fetchFailure(operation, key, FailureStatus.TIMEOUT, e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw new ProviderException(getClass(), e.getCause());
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new ProviderException(
          getClass(), "Interrupted fetching GitHub " + operation.describe(key), e);
    }
  }

  /**
   * Fetches organization members using hub4j. Pagination is handled automatically by PagedIterable.
   *
   * <p>By default, hub4j's listMembers() returns all members with state "active". Suspended or
   * pending members are excluded automatically by the GitHub API.
   *
   * <p>A 404 fails the fetch like any other error. GitHub also answers 404 for an organization the
   * credentials cannot see, so treating it as "no members" would remove every user's roles.
   */
  private Set<String> fetchOrgMembers(String orgName) {
    try {
      log.debug("Fetching members for organization: {}", orgName);

      GHOrganization org = gitHubClient.getOrganization(orgName);

      // hub4j handles pagination automatically!
      Set<String> memberLogins =
          org.listMembers().toList().stream()
              .map(user -> user.getLogin().toLowerCase())
              .collect(Collectors.toSet());

      log.info("Fetched {} members from organization {}", memberLogins.size(), orgName);
      logRateLimitInfo("fetchOrgMembers");
      return memberLogins;

    } catch (IOException e) {
      throw fetchFailure(FetchOperation.MEMBERS, orgName, e);
    }
  }

  /**
   * Fetches organization teams using hub4j. Returns a map of slug -> GHTeam for easy lookup. As for
   * {@link #fetchOrgMembers}, a 404 fails the fetch.
   */
  private Map<String, GHTeam> fetchOrgTeams(String orgName) {
    try {
      log.debug("Fetching teams for organization: {}", orgName);

      GHOrganization org = gitHubClient.getOrganization(orgName);
      // Not org.getTeams(): it walks the paged iterator directly, so a failed page surfaces as an
      // unchecked GHException. toList() rethrows it as the underlying IOException.
      Map<String, GHTeam> teams =
          org.listTeams().toList().stream()
              .collect(Collectors.toMap(GHTeam::getSlug, team -> team, (a, b) -> a, TreeMap::new));

      log.info("Fetched {} teams from organization {}", teams.size(), orgName);
      logRateLimitInfo("fetchOrgTeams");
      return teams;

    } catch (IOException e) {
      throw fetchFailure(FetchOperation.TEAMS, orgName, e);
    }
  }

  /**
   * Fetches members of a specific team using hub4j. The team comes from the cached teams listing,
   * so this costs one call per 100 members rather than also looking up the organization and the
   * team on every refresh. A 404 listing the team's members returns no members: the team is gone.
   */
  private Set<String> fetchTeamMembers(String teamSlug) {
    String orgName = gitHubProperties.getOrganization();
    try {
      log.debug("Fetching members for team: {} in organization: {}", teamSlug, orgName);

      // Slugs come from this listing, so a missing one was removed since the listing was refreshed
      GHTeam team = getCached(teamsCache, orgName).get(teamSlug);
      if (team == null) {
        log.warn("Team no longer listed: {} in organization: {}", teamSlug, orgName);
        return Collections.emptySet();
      }

      // hub4j handles pagination automatically!
      Set<String> memberLogins =
          team.listMembers().toList().stream()
              .map(user -> user.getLogin().toLowerCase())
              .collect(Collectors.toSet());

      log.debug("Fetched {} members for team {}", memberLogins.size(), teamSlug);
      return memberLogins;

    } catch (GHFileNotFoundException e) {
      log.error("GitHub team not found: {} in organization: {}", teamSlug, orgName, e);
      // For 404, return empty set (team doesn't exist, no retries will help)
      return Collections.emptySet();
    } catch (IOException e) {
      throw fetchFailure(FetchOperation.TEAM_MEMBERS, teamSlug, e);
    }
  }

  /** The GitHub data each cache fetches. */
  private enum FetchOperation {
    MEMBERS("members", "members of organization "),
    TEAMS("teams", "teams of organization "),
    TEAM_MEMBERS("teamMembers", "members of team ");

    /** The value of the {@code op} metric tag. */
    private final String tag;

    private final String descriptionPrefix;

    FetchOperation(String tag, String descriptionPrefix) {
      this.tag = tag;
      this.descriptionPrefix = descriptionPrefix;
    }

    /** Describes the data fetched for a cache key (an organization or team slug), for messages. */
    String describe(String key) {
      return descriptionPrefix + key;
    }
  }

  /** Why a GitHub fetch failed. */
  private enum FailureStatus {
    UNAUTHORIZED("401"),
    FORBIDDEN("403"),
    NOT_FOUND("404"),
    RATE_LIMITED("429"),
    OTHER_CLIENT_ERROR("4xx"),
    SERVER_ERROR("5xx"),
    /** Failed without an HTTP status, e.g. a connection error. */
    IO("io"),
    TIMEOUT("timeout");

    /** The value of the {@code status} metric tag. */
    private final String tag;

    FailureStatus(String tag) {
      this.tag = tag;
    }
  }

  /**
   * Whether a fetch failed because fetchWithTimeout cancelled it. hub4j reports the interrupt as an
   * {@link InterruptedIOException} and clears the flag, and the token mint may wrap it, so the
   * causes are checked as well as the flag.
   */
  private static boolean isCancellation(Throwable failure) {
    if (Thread.currentThread().isInterrupted()) {
      return true;
    }
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof InterruptedIOException || cause instanceof InterruptedException) {
        return true;
      }
    }
    return false;
  }

  /**
   * As {@link #fetchFailure(FetchOperation, String, FailureStatus, Exception)}, for an I/O error.
   */
  private ProviderException fetchFailure(FetchOperation operation, String key, IOException e) {
    if (isCancellation(e)) {
      // Cancelled by fetchWithTimeout, which has already logged and counted the timeout
      return new ProviderException(
          getClass(), "Cancelled fetching " + operation.describe(key) + " after a timeout", e);
    }
    return fetchFailure(operation, key, failureStatus(e), e);
  }

  /**
   * Logs a failed GitHub fetch, counts it, and wraps it so the calling cache loader can rethrow it.
   * An HTTP 401 also drops the cached installation token, so the next request mints a new one
   * rather than reusing a token GitHub has revoked. A 401 inferred from message text alone does
   * not: it is not a response from GitHub.
   *
   * <p>Every failure is rethrown rather than turned into an empty result. On a refresh, Guava then
   * keeps serving the previous value, so a network error, an expired credential or a rate limit
   * cannot replace known memberships with an empty set and remove every user's roles. On a cold
   * cache the exception fails the role sync, which leaves stored permissions unchanged.
   *
   * @param key the cache key fetched: an organization or a team slug
   * @return the exception for the caller to throw
   */
  private ProviderException fetchFailure(
      FetchOperation operation, String key, FailureStatus status, Exception e) {
    String what = operation.describe(key);
    switch (status) {
      case RATE_LIMITED ->
          log.error(
              "GitHub API rate limit exceeded fetching {}. Check rate limit at: https://api.github.com/rate_limit",
              what,
              e);
      case UNAUTHORIZED ->
          log.error(
              "GitHub authentication failed (401 Unauthorized) fetching {}. Verify credentials.",
              what,
              e);
      case FORBIDDEN ->
          log.error(
              "GitHub access forbidden (403) fetching {}. Check permissions and rate limits.",
              what,
              e);
      case NOT_FOUND ->
          log.error(
              "GitHub returned 404 Not Found fetching {}. Check the organization name and that the"
                  + " credentials can see it.",
              what,
              e);
      case TIMEOUT ->
          log.error(
              "Timed out after {}s fetching {} from GitHub",
              gitHubProperties.getRefreshTimeoutSeconds(),
              what);
      default -> log.error("Failed to fetch {} from GitHub", what, e);
    }
    meterRegistry
        .counter("fiat.github.fetchFailures", "op", operation.tag, "status", status.tag)
        .increment();

    if (status == FailureStatus.UNAUTHORIZED
        && e instanceof HttpException
        && gitHubAppAuthenticator != null) {
      gitHubAppAuthenticator.invalidateInstallationToken();
    }

    String reason = status == FailureStatus.TIMEOUT ? "Timed out" : "GitHub API error";
    return new ProviderException(
        getClass(), reason + " fetching " + what + " - using cached values if available", e);
  }

  /**
   * Classifies an I/O failure. hub4j's HttpException message is the response body, which may not
   * include the status code, so this uses the status when there is one and matches on the message
   * only for other IOExceptions. GitHub signals rate limits with 429, or with a 403 whose body
   * mentions the rate limit, so those are checked before a plain 403.
   */
  private static FailureStatus failureStatus(IOException e) {
    if (e instanceof GHFileNotFoundException) {
      return FailureStatus.NOT_FOUND;
    }
    String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
    int status = e instanceof HttpException httpException ? httpException.getResponseCode() : -1;
    if (status == 429 || message.contains("rate limit")) {
      return FailureStatus.RATE_LIMITED;
    }
    if (status <= 0) {
      return message.contains("401")
          ? FailureStatus.UNAUTHORIZED
          : message.contains("403") ? FailureStatus.FORBIDDEN : FailureStatus.IO;
    }
    return switch (status) {
      case 401 -> FailureStatus.UNAUTHORIZED;
      case 403 -> FailureStatus.FORBIDDEN;
      case 404 -> FailureStatus.NOT_FOUND;
      default -> status >= 500 ? FailureStatus.SERVER_ERROR : FailureStatus.OTHER_CLIENT_ERROR;
    };
  }

  /**
   * Logs GitHub API rate limit information for debugging and monitoring.
   *
   * <p>This helps track API usage and identify potential rate limit issues before they occur.
   *
   * @param operation The operation that just completed (for context in logs)
   */
  private void logRateLimitInfo(String operation) {
    try {
      GHRateLimit rateLimit = gitHubClient.getRateLimit();

      // Handle null rate limit (can happen in tests or with certain GitHub API responses)
      if (rateLimit == null) {
        log.debug("Rate limit info not available after {}", operation);
        return;
      }

      GHRateLimit.Record core = rateLimit.getCore();

      // Handle null core record
      if (core == null) {
        log.debug("Rate limit core record not available after {}", operation);
        return;
      }

      int limit = core.getLimit();
      int remaining = core.getRemaining();
      rateLimitRemaining.set(remaining);

      log.debug(
          "GitHub rate limit after {}: {}/{} remaining, resets at {}",
          operation,
          remaining,
          limit,
          core.getResetDate());

      // Guard against division by zero - can happen in tests or unexpected API responses
      if (limit <= 0) {
        log.debug(
            "Rate limit value is {} after {}, skipping percentage calculation", limit, operation);
        return;
      }

      // Warn if rate limit is getting low (less than 10% remaining)
      if (remaining < limit * 0.1) {
        log.warn(
            "GitHub API rate limit is running low: {}/{} remaining ({}%). Resets at {}",
            remaining, limit, (remaining * 100 / limit), core.getResetDate());
      }
    } catch (IOException e) {
      log.debug("Could not fetch rate limit info after {}", operation, e);
    } catch (Exception e) {
      log.debug("Unexpected error fetching rate limit info after {}", operation, e);
    }
  }

  /**
   * Loads all roles for a given user based on GitHub organization and team memberships.
   *
   * <p><b>Role Assignment Logic:</b>
   *
   * <ol>
   *   <li>Checks if user is an active member of the configured organization
   *   <li>If not a member, returns empty list (no roles)
   *   <li>If member, assigns organization role (e.g., "my-org")
   *   <li>Checks membership in each team within the organization
   *   <li>Assigns team role for each team the user belongs to (e.g., "team-developers")
   * </ol>
   *
   * <p><b>Example:</b> For user "john" in organization "acme" who is member of teams "developers"
   * and "reviewers", returns roles: ["acme", "developers", "reviewers"]
   *
   * <p><b>Note:</b> All role names are converted to lowercase for consistency.
   *
   * @param user External user to load roles for (must have a valid ID/username)
   * @return List of roles (organization + teams), or empty list if not an org member
   * @throws ProviderException if GitHub data is needed that is not cached and cannot be fetched
   */
  @Override
  public List<Role> loadRoles(ExternalUser user) {
    String username = user.getId();

    log.debug("Loading roles for user: {}", username);

    if (StringUtils.isEmpty(username) || StringUtils.isEmpty(gitHubProperties.getOrganization())) {
      return new ArrayList<>();
    }

    if (!isMemberOfOrg(username)) {
      log.debug(
          "User {} is not a member of organization {}",
          username,
          gitHubProperties.getOrganization());
      return new ArrayList<>();
    }

    log.debug(
        "User {} is a member of organization {}", username, gitHubProperties.getOrganization());

    List<Role> result = new ArrayList<>();
    result.add(toRole(gitHubProperties.getOrganization()));

    // Get teams of the org
    Map<String, GHTeam> teams = getTeams();
    log.debug("Found {} teams in organization", teams.size());

    teams
        .values()
        .forEach(
            team -> {
              String teamSlug = team.getSlug();
              String teamName = team.getName();
              String debugMsg = username + " is a member of team " + teamName;

              if (isMemberOfTeam(teamSlug, username)) {
                result.add(toRole(teamSlug));
                debugMsg += ": true";
              } else {
                debugMsg += ": false";
              }
              log.debug(debugMsg);
            });

    return result;
  }

  private boolean isMemberOfOrg(String username) {
    return getCached(membersCache, gitHubProperties.getOrganization())
        .contains(username.toLowerCase());
  }

  private Map<String, GHTeam> getTeams() {
    return getCached(teamsCache, gitHubProperties.getOrganization());
  }

  private boolean isMemberOfTeam(String teamSlug, String username) {
    return getCached(teamMembershipCache, teamSlug).contains(username.toLowerCase());
  }

  /**
   * Reads from a cache, surfacing a failed load as a {@link ProviderException} so that Fiat's
   * permission resolution and role sync treat it as a provider failure.
   *
   * <p>Data whose refreshes have kept failing for more than {@code maxStaleRolesSeconds} is refused
   * the same way, so roles are not served indefinitely from a cache GitHub can no longer update.
   * The role sync then aborts and the stored permissions stay as they are; it does not revoke them.
   */
  private <V> V getCached(LoadingCache<String, Fetched<V>> cache, String key) {
    Fetched<V> fetched;
    try {
      fetched = cache.get(key);
    } catch (ExecutionException | UncheckedExecutionException e) {
      if (e.getCause() instanceof ProviderException) {
        throw (ProviderException) e.getCause();
      }
      throw new ProviderException(this.getClass(), e.getCause());
    }

    int maxStaleSeconds = gitHubProperties.getMaxStaleRolesSeconds();
    Long failingSince = fetched.failingSinceNanos();
    if (maxStaleSeconds > 0 && failingSince != null) {
      long now = ticker.read();
      long failingSeconds = TimeUnit.NANOSECONDS.toSeconds(now - failingSince);
      if (failingSeconds > maxStaleSeconds) {
        meterRegistry.counter("fiat.github.staleRejections").increment();
        throw new ProviderException(
            getClass(),
            "Cached GitHub data for "
                + key
                + " is stale: refreshes have failed for "
                + failingSeconds
                + "s, more than maxStaleRolesSeconds ("
                + maxStaleSeconds
                + "s), and it was last fetched "
                + TimeUnit.NANOSECONDS.toSeconds(now - fetched.fetchedAtNanos())
                + "s ago. See the earlier GitHub errors.");
      }
    }
    return fetched.value();
  }

  private static Role toRole(String name) {
    return new Role().setName(name.toLowerCase()).setSource(Role.Source.GITHUB_TEAMS);
  }

  /**
   * Loads roles for multiple users in a single operation.
   *
   * <p>This is a convenience method that calls {@link #loadRoles(ExternalUser)} for each user. The
   * GitHub API cache is shared across all users, so this is efficient for batch operations.
   *
   * <p><b>Performance:</b> Cached data (org members, teams) is reused across all users in the
   * batch, making this much more efficient than individual calls for large user sets.
   *
   * @param users Collection of external users to load roles for
   * @return Map of user ID to their collection of roles, or empty map if input is null/empty
   */
  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (users == null || users.isEmpty()) {
      return new HashMap<>();
    }

    val emailGroupsMap = new HashMap<String, Collection<Role>>();
    users.forEach(u -> emailGroupsMap.put(u.getId(), loadRoles(u)));

    return emailGroupsMap;
  }

  /**
   * Invalidates all caches (members, teams, and team memberships).
   *
   * <p>This method is primarily intended for testing purposes to force fresh fetches from the
   * GitHub API. In production, the caches refresh automatically based on the configured refresh
   * intervals.
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>Testing error scenarios that require fresh API calls
   *   <li>Manual cache clearing during debugging
   *   <li>Forcing cache refresh after configuration changes
   * </ul>
   */
  public void invalidateAll() {
    if (membersCache != null) {
      membersCache.invalidateAll();
    }
    if (teamsCache != null) {
      teamsCache.invalidateAll();
    }
    if (teamMembershipCache != null) {
      teamMembershipCache.invalidateAll();
    }
    log.debug("All GitHub caches invalidated (members, teams, team memberships)");
  }

  /**
   * Invalidates the organization members cache.
   *
   * <p>This forces a fresh fetch of organization members on the next access. Team and team
   * membership caches are not affected.
   */
  public void invalidateMembersCache() {
    if (membersCache != null) {
      membersCache.invalidateAll();
      log.debug("GitHub members cache invalidated");
    }
  }

  /**
   * Invalidates the organization teams cache.
   *
   * <p>This forces a fresh fetch of organization teams on the next access. Members and team
   * membership caches are not affected.
   */
  public void invalidateTeamsCache() {
    if (teamsCache != null) {
      teamsCache.invalidateAll();
      log.debug("GitHub teams cache invalidated");
    }
  }

  /**
   * Invalidates the team memberships cache.
   *
   * <p>This forces a fresh fetch of team memberships on the next access. Members and teams caches
   * are not affected.
   */
  public void invalidateTeamMembershipsCache() {
    if (teamMembershipCache != null) {
      teamMembershipCache.invalidateAll();
      log.debug("GitHub team memberships cache invalidated");
    }
  }

  /**
   * Invalidates a specific team's membership cache.
   *
   * <p>This allows selective cache invalidation for a single team without affecting other cached
   * data.
   *
   * @param teamSlug The team slug to invalidate
   */
  public void invalidateTeamMembership(String teamSlug) {
    if (teamMembershipCache != null && teamSlug != null) {
      teamMembershipCache.invalidate(teamSlug);
      log.debug("GitHub team membership cache invalidated for team: {}", teamSlug);
    }
  }

  /**
   * Returns the current size of all caches combined.
   *
   * <p>This method is primarily intended for monitoring and testing purposes.
   *
   * @return Total number of cached entries across all caches
   */
  public long getCacheSize() {
    long size = 0;
    if (membersCache != null) {
      size += membersCache.size();
    }
    if (teamsCache != null) {
      size += teamsCache.size();
    }
    if (teamMembershipCache != null) {
      size += teamMembershipCache.size();
    }
    return size;
  }
}
