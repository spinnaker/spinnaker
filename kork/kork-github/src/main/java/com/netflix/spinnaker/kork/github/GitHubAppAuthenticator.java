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

import com.google.common.util.concurrent.Striped;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Handles GitHub App authentication for Spinnaker components.
 *
 * <p>This class is reusable across Fiat, Clouddriver, Igor, and other services that need to
 * interact with GitHub as a GitHub App.
 *
 * <h2>Operating modes</h2>
 *
 * <ul>
 *   <li><b>Pinned</b> - an installation ID is supplied at construction and every token is minted
 *       for that installation. Use {@link #getInstallationToken()}.
 *   <li><b>Derived</b> - no installation ID is supplied and the installation is resolved from the
 *       repository being accessed, so one authenticator can serve several organizations. Use {@link
 *       #getInstallationTokenForRepo(String, String)}.
 * </ul>
 *
 * <p>The two modes are mutually exclusive: the pinned accessor throws in derive mode, and the
 * derive accessor is the only path that resolves installations. Each mode guards a distinct set of
 * cache keys, so a single authenticator never mints concurrently for the same installation through
 * both paths.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Loading the app private key, and reloading it when the key file changes so a rotated key
 *       needs no restart
 *   <li>Signing JWTs, with the issue time set slightly in the past to tolerate clock skew
 *   <li>Resolving installations (derive mode) and minting installation tokens
 *   <li>Caching tokens per installation, refreshed shortly before expiry, so cache hits make no
 *       GitHub API calls. If a refresh fails while the cached token is still valid, the cached
 *       token keeps being served
 *   <li>Enforcing the optional organization allowlist. This is authorization policy living inside
 *       an authentication primitive - a deliberate trade-off, so that the check cannot be bypassed
 *       by a caller that forgets it, and so rejection costs no API call.
 *   <li>Support for both GitHub.com and GitHub Enterprise
 * </ul>
 */
@Slf4j
public class GitHubAppAuthenticator {

  /**
   * How far a JWT's issue time is set in the past. GitHub rejects a JWT issued in its future, so
   * this absorbs a host clock running ahead of GitHub's (as GitHub's documentation recommends).
   */
  private static final Duration JWT_BACKDATE = Duration.ofSeconds(60);

  /**
   * How long after now a JWT expires. GitHub rejects an expiry more than 10 minutes after its own
   * now, so this stays below that for a host clock running ahead.
   */
  private static final Duration JWT_VALIDITY = Duration.ofMinutes(9);

  /** How long before a cached token expires that it is renewed. */
  private static final Duration TOKEN_REFRESH_BUFFER = Duration.ofMinutes(5);

  /**
   * How long to keep serving a still-valid cached token after renewing it failed, before trying
   * again. Every request asks for the token, so without this each one would retry the mint (three
   * GitHub calls) for as long as GitHub keeps failing.
   */
  private static final Duration RENEWAL_RETRY_DELAY = Duration.ofSeconds(30);

  private static final int MINT_LOCK_STRIPES = 32;

  private final String appId;
  private final Path privateKeyPath;

  /** Replaced when the key file changes, so a rotated key is picked up without a restart. */
  private volatile PrivateKey privateKey;

  /**
   * SHA-256 digest of the key file's contents when {@link #privateKey} was loaded from it. A
   * digest, not the contents, so that no second copy of the key is kept.
   */
  @Nullable private volatile byte[] privateKeyDigest;

  @Nullable private final String installationId;
  private final String baseUrl;
  private final Clock clock;

  /** Owners this authenticator may derive installations for; empty means no restriction. */
  private final Set<String> allowedOrganizations;

  /** Installation tokens cached per installation ID. */
  private final Map<String, CachedToken> tokensByInstallationId = new ConcurrentHashMap<>();

  /**
   * Installation IDs resolved per repository owner (lower-cased), so that cached tokens can be
   * served without re-resolving the installation on every call.
   */
  private final Map<String, String> installationIdsByOwner = new ConcurrentHashMap<>();

  /**
   * Locks guarding token minting, striped so that minting for one owner does not block others.
   * Striping is used rather than a lock per key because derive-mode keys come from the repository
   * being accessed, which is user-supplied: a per-key map would grow without bound.
   */
  private final Striped<Lock> mintLocks = Striped.lock(MINT_LOCK_STRIPES);

  /**
   * Creates a GitHub App authenticator that may access any repository owner where the app is
   * installed.
   *
   * @param appId GitHub App ID
   * @param privateKeyPath Path to the PEM-encoded private key file (PKCS#1 or PKCS#8)
   * @param installationId GitHub App installation ID, or null to derive the installation from the
   *     repository being accessed (see {@link #getInstallationTokenForRepo(String, String)})
   * @param baseUrl GitHub API base URL (e.g., "https://api.github.com" or GitHub Enterprise URL)
   */
  public GitHubAppAuthenticator(
      String appId, String privateKeyPath, @Nullable String installationId, String baseUrl) {
    this(appId, privateKeyPath, installationId, baseUrl, Set.of());
  }

  /**
   * Creates a GitHub App authenticator restricted to a set of repository owners.
   *
   * @param appId GitHub App ID
   * @param privateKeyPath Path to the PEM-encoded private key file (PKCS#1 or PKCS#8)
   * @param installationId GitHub App installation ID, or null to derive the installation from the
   *     repository being accessed (see {@link #getInstallationTokenForRepo(String, String)})
   * @param baseUrl GitHub API base URL (e.g., "https://api.github.com" or GitHub Enterprise URL)
   * @param allowedOrganizations lower-cased repository owners this authenticator may mint derived
   *     installation tokens for; empty allows any owner where the app is installed. Has no effect
   *     when an installation ID is configured.
   */
  public GitHubAppAuthenticator(
      String appId,
      String privateKeyPath,
      @Nullable String installationId,
      String baseUrl,
      Set<String> allowedOrganizations) {
    this(appId, privateKeyPath, installationId, baseUrl, allowedOrganizations, Clock.systemUTC());
  }

  /** As the public constructors, with the clock used for JWTs and token expiry; for tests. */
  GitHubAppAuthenticator(
      String appId,
      String privateKeyPath,
      @Nullable String installationId,
      String baseUrl,
      Set<String> allowedOrganizations,
      Clock clock) {
    this.appId = appId;
    this.privateKeyPath = Paths.get(privateKeyPath);
    this.privateKeyDigest = digestOf(this.privateKeyPath);
    this.privateKey = loadPrivateKey(privateKeyPath);
    this.installationId = installationId;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.allowedOrganizations = Set.copyOf(allowedOrganizations);
    this.clock = clock;
  }

  /**
   * Returns a GitHub client authenticated as the configured installation.
   *
   * <p>The client asks this authenticator for credentials on every request (through {@link
   * #getInstallationToken()}), so it keeps working after the one-hour installation token lifetime
   * and is safe to hold long-term, including as a singleton Spring {@code @Bean}. Requests served
   * from the token cache make no extra GitHub API calls; a new token is minted only shortly before
   * the cached one expires.
   *
   * <p>A token is minted eagerly before the client is returned, so invalid app credentials or an
   * unreachable endpoint fail here rather than on the first request.
   *
   * <p>Requires a configured installation ID; in derive mode use {@link
   * #getInstallationTokenForRepo(String, String)} instead.
   *
   * @return Authenticated GitHub client that renews its installation token as needed
   * @throws IOException if authentication fails
   * @throws IllegalStateException if no installation ID was configured
   */
  public GitHub getAuthenticatedClient() throws IOException {
    // Fail fast on bad credentials, and warm the token cache for the first request.
    getInstallationToken();

    return new GitHubBuilder()
        .withEndpoint(baseUrl)
        .withAuthorizationProvider(() -> "token " + getInstallationToken())
        .build();
  }

  /**
   * Gets a valid installation token for the configured installation, using cache when possible.
   *
   * <p>Cached tokens are returned without contacting the GitHub API. If renewing a token fails
   * while the cached one has not yet expired, the cached token is returned, and renewal is retried
   * once a short delay has passed.
   *
   * @return Valid installation token
   * @throws IOException if token cannot be obtained
   * @throws IllegalStateException if no installation ID was configured (derive mode - use {@link
   *     #getInstallationTokenForRepo(String, String)} instead)
   */
  public String getInstallationToken() throws IOException {
    String installationId = requireInstallationId();
    CachedToken cached = tokensByInstallationId.get(installationId);
    if (cached != null && !isTokenExpired(cached)) {
      return cached.token;
    }
    Lock lock = mintLocks.get("installation:" + installationId);
    lock.lock();
    try {
      cached = tokensByInstallationId.get(installationId);
      if (cached != null && (!isTokenExpired(cached) || renewalRecentlyFailed(cached))) {
        return cached.token;
      }
      try {
        CachedToken fresh = mintToken(resolveInstallationById(installationId), installationId);
        tokensByInstallationId.put(installationId, fresh);
        return fresh.token;
      } catch (IOException e) {
        if (isInterruption(e)) {
          Thread.currentThread().interrupt();
          throw e;
        }
        return unexpiredTokenOrThrow(cached, "installation " + installationId, e);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Gets a valid installation token for the installation of this GitHub App that has access to the
   * given repository, using cache when possible.
   *
   * <p>The owner may be an organization or a user account. A cached, unexpired token for the owner
   * is returned without contacting the GitHub API; the installation is only resolved when a token
   * has to be minted (at most once per refresh window per owner), so uninstalling and reinstalling
   * the app is picked up automatically.
   *
   * @param owner the repository owner (organization or user)
   * @param repo the repository name
   * @return Valid installation token for the owner's installation
   * @throws IOException if no installation has access to the repository, or the token cannot be
   *     obtained
   * @throws IllegalArgumentException if the owner is not permitted by the configured organization
   *     allowlist
   */
  public String getInstallationTokenForRepo(String owner, String repo) throws IOException {
    String ownerKey = owner.toLowerCase();
    // Checked before any API call so that a rejected owner costs nothing and reveals nothing
    if (!allowedOrganizations.isEmpty() && !allowedOrganizations.contains(ownerKey)) {
      throw new IllegalArgumentException(
          "GitHub App "
              + appId
              + " is not permitted to access repositories owned by '"
              + owner
              + "'. Allowed organizations: "
              + new TreeSet<>(allowedOrganizations)
              + ".");
    }
    CachedToken cached = cachedTokenForOwner(ownerKey);
    if (cached != null && !isTokenExpired(cached)) {
      return cached.token;
    }
    Lock lock = mintLocks.get("owner:" + ownerKey);
    lock.lock();
    try {
      cached = cachedTokenForOwner(ownerKey);
      if (cached != null && (!isTokenExpired(cached) || renewalRecentlyFailed(cached))) {
        return cached.token;
      }
      try {
        GHAppInstallation installation = resolveInstallationByRepo(owner, repo);
        String resolvedInstallationId = String.valueOf(installation.getId());
        CachedToken fresh = mintToken(installation, resolvedInstallationId);
        tokensByInstallationId.put(resolvedInstallationId, fresh);
        installationIdsByOwner.put(ownerKey, resolvedInstallationId);
        return fresh.token;
      } catch (IOException e) {
        if (isInterruption(e)) {
          Thread.currentThread().interrupt();
          throw e;
        }
        return unexpiredTokenOrThrow(cached, "owner '" + owner + "'", e);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Drops the cached token for the configured installation, so that the next request mints a new
   * one. Use it when GitHub rejects a cached token that has not yet expired, for example after the
   * app was suspended and reinstated.
   *
   * @throws IllegalStateException if no installation ID was configured
   */
  public void invalidateInstallationToken() {
    tokensByInstallationId.remove(requireInstallationId());
  }

  /**
   * Returns when the cached token for the configured installation expires.
   *
   * @return the expiry, or null if no token has been minted yet
   * @throws IllegalStateException if no installation ID was configured
   */
  @Nullable
  public Instant getInstallationTokenExpiry() {
    CachedToken cached = tokensByInstallationId.get(requireInstallationId());
    return cached != null ? cached.expiresAt : null;
  }

  private String requireInstallationId() {
    if (installationId == null) {
      throw new IllegalStateException(
          "No GitHub App installation ID configured for app "
              + appId
              + ". Use getInstallationTokenForRepo to derive the installation from the repository.");
    }
    return installationId;
  }

  /**
   * Returns the cached token for an owner, which may be due for renewal, or null when the owner's
   * installation has not been resolved yet.
   */
  @Nullable
  private CachedToken cachedTokenForOwner(String ownerKey) {
    String knownInstallationId = installationIdsByOwner.get(ownerKey);
    return knownInstallationId != null ? tokensByInstallationId.get(knownInstallationId) : null;
  }

  /**
   * Returns a cached token whose renewal failed, as long as GitHub will still accept it, so that a
   * GitHub outage inside the renewal window does not fail requests early.
   *
   * @throws IOException {@code failure}, if there is no cached token or it has expired
   */
  private String unexpiredTokenOrThrow(
      @Nullable CachedToken cached, String description, IOException failure) throws IOException {
    if (cached == null || cached.expiresAt == null || !cached.expiresAt.isAfter(clock.instant())) {
      throw failure;
    }
    cached.renewalFailedAt = clock.instant();
    log.warn(
        "Failed to renew the GitHub App installation token for app {} {}; using the cached token, which expires at {}, and retrying in {}s",
        appId,
        description,
        cached.expiresAt,
        RENEWAL_RETRY_DELAY.toSeconds(),
        failure);
    return cached.token;
  }

  /**
   * Whether renewing this still-valid token failed within the last {@link #RENEWAL_RETRY_DELAY}.
   */
  private boolean renewalRecentlyFailed(CachedToken cached) {
    Instant failedAt = cached.renewalFailedAt;
    Instant now = clock.instant();
    return failedAt != null
        && now.isBefore(failedAt.plus(RENEWAL_RETRY_DELAY))
        && cached.expiresAt != null
        && cached.expiresAt.isAfter(now);
  }

  /**
   * Whether a renewal failed because the calling thread was interrupted, e.g. cancelled by a
   * timeout, rather than because of GitHub. hub4j reports an interrupt as an {@link
   * InterruptedIOException} and clears the interrupt flag, and the mint wraps it, so this checks
   * the causes as well as the flag.
   */
  private static boolean isInterruption(Throwable failure) {
    if (Thread.currentThread().isInterrupted()) {
      return true;
    }
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof InterruptedIOException
          || cause instanceof InterruptedException
          || cause instanceof ClosedByInterruptException) {
        return true;
      }
    }
    return false;
  }

  private GHAppInstallation resolveInstallationByRepo(String owner, String repo)
      throws IOException {
    GHApp app = authenticateAsApp();
    try {
      return app.getInstallationByRepository(owner, repo);
    } catch (GHFileNotFoundException e) {
      throw new IOException(
          "GitHub App "
              + appId
              + " has no installation with access to '"
              + owner
              + "/"
              + repo
              + "' on "
              + baseUrl
              + ". Install the app in '"
              + owner
              + "' and grant it access to the repository.",
          e);
    } catch (IOException e) {
      throw new IOException(
          "Failed to resolve the GitHub App installation for '"
              + owner
              + "/"
              + repo
              + "': "
              + e.getMessage(),
          e);
    }
  }

  private GHAppInstallation resolveInstallationById(String installationId) throws IOException {
    GHApp app = authenticateAsApp();
    try {
      return app.getInstallationById(Long.parseLong(installationId));
    } catch (IOException e) {
      throw new IOException(
          "Failed to look up GitHub App installation "
              + installationId
              + " on "
              + baseUrl
              + ": "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Authenticates as the app itself using a freshly signed JWT. Failures here indicate a problem
   * with the app credentials or endpoint rather than with a specific installation.
   */
  private GHApp authenticateAsApp() throws IOException {
    GitHub client = new GitHubBuilder().withEndpoint(baseUrl).withJwtToken(generateJWT()).build();
    try {
      return client.getApp();
    } catch (IOException e) {
      throw new IOException(
          "Failed to authenticate as GitHub App "
              + appId
              + " against "
              + baseUrl
              + " (check githubApp.appId, the private key, apiBaseUrl and this host's clock): "
              + e.getMessage(),
          e);
    }
  }

  private CachedToken mintToken(GHAppInstallation installation, String installationId)
      throws IOException {
    GHAppInstallationToken token = installation.createToken().create();

    log.debug(
        "Successfully obtained GitHub App installation token for app {} installation {}, expires at: {}",
        appId,
        installationId,
        token.getExpiresAt());

    return new CachedToken(token.getToken(), token.getExpiresAt().toInstant());
  }

  private String generateJWT() {
    reloadPrivateKeyIfChanged();
    Instant now = clock.instant();

    return Jwts.builder()
        .setIssuer(appId)
        .setIssuedAt(Date.from(now.minus(JWT_BACKDATE)))
        .setExpiration(Date.from(now.plus(JWT_VALIDITY)))
        .signWith(privateKey, SignatureAlgorithm.RS256)
        .compact();
  }

  /**
   * Reloads the private key if its file's contents have changed since it was loaded, so that a
   * rotated key takes effect at the next mint. Contents rather than the modification time, which a
   * copy can preserve (e.g. {@code cp -p}). This runs only when a JWT is signed, about once an hour
   * per installation, so reading the small key file each time is cheap. If the changed file cannot
   * be loaded, the current key stays in use and the reload is retried at the next mint.
   */
  private synchronized void reloadPrivateKeyIfChanged() {
    byte[] digest = digestOf(privateKeyPath);
    if (digest == null || Arrays.equals(digest, privateKeyDigest)) {
      return;
    }
    try {
      privateKey = loadPrivateKey(privateKeyPath.toString());
      privateKeyDigest = digest;
      log.info("Reloaded the changed GitHub App private key for app {}", appId);
    } catch (RuntimeException e) {
      log.error(
          "Failed to reload the changed GitHub App private key for app {}; still using the previous key",
          appId,
          e);
    }
  }

  @Nullable
  private static byte[] digestOf(Path file) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    } catch (IOException | NoSuchAlgorithmException e) {
      log.warn("Unable to read {} to check it for changes", file, e);
      return null;
    }
  }

  private boolean isTokenExpired(CachedToken token) {
    if (token == null || token.expiresAt == null) {
      return true;
    }

    return token.expiresAt.isBefore(clock.instant().plus(TOKEN_REFRESH_BUFFER));
  }

  /**
   * Loads a private key from a PEM file. Supports both PKCS#1 (RSA PRIVATE KEY) and PKCS#8 (PRIVATE
   * KEY) formats.
   *
   * @param privateKeyPath Path to the PEM file
   * @return Loaded private key
   * @throws RuntimeException if the key cannot be loaded
   */
  private PrivateKey loadPrivateKey(String privateKeyPath) {
    try (Reader keyReader = Files.newBufferedReader(Paths.get(privateKeyPath));
        PEMParser pemParser = new PEMParser(keyReader)) {

      Object object = pemParser.readObject();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

      if (object instanceof PEMKeyPair) {
        // For PKCS#1 format (-----BEGIN RSA PRIVATE KEY-----)
        return converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
      } else if (object instanceof PrivateKeyInfo) {
        // For PKCS#8 format (-----BEGIN PRIVATE KEY-----)
        return converter.getPrivateKey((PrivateKeyInfo) object);
      } else {
        throw new IllegalArgumentException(
            "Unsupported PEM format. Expected RSA private key, got: "
                + (object != null ? object.getClass().getName() : "null"));
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to load GitHub App private key from: " + privateKeyPath, e);
    }
  }

  /** Cached token with expiration time */
  private static class CachedToken {
    final String token;
    final Instant expiresAt;

    /** When renewing this token last failed, or null; set and read under the mint lock. */
    @Nullable volatile Instant renewalFailedAt;

    CachedToken(String token, Instant expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }
  }
}
