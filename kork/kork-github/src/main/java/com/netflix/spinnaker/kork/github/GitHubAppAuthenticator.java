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
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
 *   <li>Loading the app private key and signing JWTs
 *   <li>Resolving installations (derive mode) and minting installation tokens
 *   <li>Caching tokens per installation, refreshed shortly before expiry, so cache hits make no
 *       GitHub API calls
 *   <li>Enforcing the optional organization allowlist. This is authorization policy living inside
 *       an authentication primitive - a deliberate trade-off, so that the check cannot be bypassed
 *       by a caller that forgets it, and so rejection costs no API call.
 *   <li>Support for both GitHub.com and GitHub Enterprise
 * </ul>
 */
@Slf4j
public class GitHubAppAuthenticator {

  private static final int JWT_EXPIRATION_MINUTES = 10;
  private static final int TOKEN_REFRESH_BUFFER_MINUTES = 5;
  private static final int MINT_LOCK_STRIPES = 32;

  private final String appId;
  private final PrivateKey privateKey;
  @Nullable private final String installationId;
  private final String baseUrl;

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
    this.appId = appId;
    this.privateKey = loadPrivateKey(privateKeyPath);
    this.installationId = installationId;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.allowedOrganizations = Set.copyOf(allowedOrganizations);
  }

  /**
   * Returns an authenticated GitHub client using installation token.
   *
   * <p><b>Important:</b> The returned {@link GitHub} client does NOT automatically refresh tokens.
   * GitHub App installation tokens expire after 1 hour. This method handles token caching and will
   * return a client with a valid token at the time of the call, but callers should:
   *
   * <ul>
   *   <li>Call this method for each operation (or batch of operations) rather than caching the
   *       client long-term
   *   <li>Or implement their own refresh logic by calling {@link #getInstallationToken()} directly
   * </ul>
   *
   * <p>For Spring applications, consider NOT using this as a singleton {@code @Bean}. Instead,
   * inject the {@link GitHubAppAuthenticator} and call this method when needed.
   *
   * <p>Requires a configured installation ID; in derive mode use {@link
   * #getInstallationTokenForRepo(String, String)} instead.
   *
   * @return Authenticated GitHub client with a currently-valid token
   * @throws IOException if authentication fails
   * @throws IllegalStateException if no installation ID was configured
   */
  public GitHub getAuthenticatedClient() throws IOException {
    String token = getInstallationToken();

    return new GitHubBuilder().withEndpoint(baseUrl).withAppInstallationToken(token).build();
  }

  /**
   * Gets a valid installation token for the configured installation, using cache when possible.
   *
   * <p>Cached tokens are returned without contacting the GitHub API.
   *
   * @return Valid installation token
   * @throws IOException if token cannot be obtained
   * @throws IllegalStateException if no installation ID was configured (derive mode - use {@link
   *     #getInstallationTokenForRepo(String, String)} instead)
   */
  public String getInstallationToken() throws IOException {
    if (installationId == null) {
      throw new IllegalStateException(
          "No GitHub App installation ID configured for app "
              + appId
              + ". Use getInstallationTokenForRepo to derive the installation from the repository.");
    }
    CachedToken cached = tokensByInstallationId.get(installationId);
    if (cached != null && !isTokenExpired(cached)) {
      return cached.token;
    }
    Lock lock = mintLocks.get("installation:" + installationId);
    lock.lock();
    try {
      cached = tokensByInstallationId.get(installationId);
      if (cached != null && !isTokenExpired(cached)) {
        return cached.token;
      }
      CachedToken fresh = mintToken(resolveInstallationById(installationId), installationId);
      tokensByInstallationId.put(installationId, fresh);
      return fresh.token;
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
    if (cached != null) {
      return cached.token;
    }
    Lock lock = mintLocks.get("owner:" + ownerKey);
    lock.lock();
    try {
      cached = cachedTokenForOwner(ownerKey);
      if (cached != null) {
        return cached.token;
      }
      GHAppInstallation installation = resolveInstallationByRepo(owner, repo);
      String resolvedInstallationId = String.valueOf(installation.getId());
      CachedToken fresh = mintToken(installation, resolvedInstallationId);
      tokensByInstallationId.put(resolvedInstallationId, fresh);
      installationIdsByOwner.put(ownerKey, resolvedInstallationId);
      return fresh.token;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the cached, unexpired token for an owner, or null when a token has to be minted. */
  @Nullable
  private CachedToken cachedTokenForOwner(String ownerKey) {
    String knownInstallationId = installationIdsByOwner.get(ownerKey);
    if (knownInstallationId == null) {
      return null;
    }
    CachedToken cached = tokensByInstallationId.get(knownInstallationId);
    return cached != null && !isTokenExpired(cached) ? cached : null;
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
    Instant now = Instant.now();
    Date issuedAt = Date.from(now);
    Date expiresAt = Date.from(now.plusSeconds(TimeUnit.MINUTES.toSeconds(JWT_EXPIRATION_MINUTES)));

    return Jwts.builder()
        .setIssuer(appId)
        .setIssuedAt(issuedAt)
        .setExpiration(expiresAt)
        .signWith(privateKey, SignatureAlgorithm.RS256)
        .compact();
  }

  private boolean isTokenExpired(CachedToken token) {
    if (token == null || token.expiresAt == null) {
      return true;
    }

    Instant refreshThreshold =
        Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(TOKEN_REFRESH_BUFFER_MINUTES));
    return token.expiresAt.isBefore(refreshThreshold);
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

    CachedToken(String token, Instant expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }
  }
}
