/*
 * Copyright 2025 Netflix, Inc.
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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Handles GitHub App authentication for Spinnaker components.
 *
 * <p>This class is reusable across Fiat, Clouddriver, Igor, and other services that need to
 * interact with GitHub as a GitHub App.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>JWT generation for GitHub App authentication
 *   <li>Installation token management with automatic refresh
 *   <li>Thread-safe token caching
 *   <li>Support for both GitHub.com and GitHub Enterprise
 * </ul>
 */
@Slf4j
public class GitHubAppAuthenticator {

  private static final int JWT_EXPIRATION_MINUTES = 10;
  private static final int TOKEN_REFRESH_BUFFER_MINUTES = 5;

  private final String appId;
  private final PrivateKey privateKey;
  private final String installationId;
  private final String baseUrl;

  private volatile CachedToken cachedToken;

  /**
   * Creates a GitHub App authenticator.
   *
   * @param appId GitHub App ID
   * @param privateKeyPath Path to the PEM-encoded private key file (PKCS#1 or PKCS#8)
   * @param installationId GitHub App installation ID
   * @param baseUrl GitHub API base URL (e.g., "https://api.github.com" or GitHub Enterprise URL)
   */
  public GitHubAppAuthenticator(
      String appId, String privateKeyPath, String installationId, String baseUrl) {
    this.appId = appId;
    this.privateKey = loadPrivateKey(privateKeyPath);
    this.installationId = installationId;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  /**
   * Returns an authenticated GitHub client using installation token. Handles token caching and
   * automatic refresh.
   *
   * @return Authenticated GitHub client
   * @throws IOException if authentication fails
   */
  public GitHub getAuthenticatedClient() throws IOException {
    String token = getInstallationToken();

    return new GitHubBuilder().withEndpoint(baseUrl).withAppInstallationToken(token).build();
  }

  /**
   * Gets a valid installation token, using cache when possible.
   *
   * @return Valid installation token
   * @throws IOException if token cannot be obtained
   */
  public String getInstallationToken() throws IOException {
    if (cachedToken == null || isTokenExpired(cachedToken)) {
      synchronized (this) {
        if (cachedToken == null || isTokenExpired(cachedToken)) {
          cachedToken = fetchInstallationToken();
        }
      }
    }
    return cachedToken.token;
  }

  private CachedToken fetchInstallationToken() throws IOException {
    String jwt = generateJWT();

    // Use hub4j to get installation token
    GitHub gitHubApp = new GitHubBuilder().withEndpoint(baseUrl).withJwtToken(jwt).build();

    GHAppInstallation installation =
        gitHubApp.getApp().getInstallationById(Long.parseLong(installationId));

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
