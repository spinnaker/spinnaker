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

package com.netflix.spinnaker.fiat.roles.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.util.StringUtils;

@Slf4j
public class GitHubAppAuthService {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final int JWT_EXPIRATION_MINUTES = 10;
  private static final int TOKEN_REFRESH_BUFFER_MINUTES = 5;

  private final String appId;
  private final PrivateKey privateKey;
  private final String installationId;
  private final String baseUrl;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  private volatile InstallationToken cachedToken;

  public GitHubAppAuthService(
      String appId,
      String privateKeyPath,
      String installationId,
      String baseUrl,
      OkHttpClient httpClient) {
    this.appId = appId;
    this.privateKey = loadPrivateKey(privateKeyPath);
    this.installationId = installationId;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();
  }

  public String getInstallationToken() throws IOException {
    if (cachedToken == null || isTokenExpired(cachedToken)) {
      synchronized (this) {
        if (cachedToken == null || isTokenExpired(cachedToken)) {
          cachedToken = fetchInstallationToken();
        }
      }
    }
    return cachedToken.getToken();
  }

  private InstallationToken fetchInstallationToken() throws IOException {
    String jwt = generateJWT();

    String url = baseUrl + "app/installations/" + installationId + "/access_tokens";
    RequestBody body = RequestBody.create("{}", JSON);

    Request request =
        new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer " + jwt)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", "Spinnaker-Fiat")
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        throw new IOException(
            "Failed to get installation token. Status: "
                + response.code()
                + ", Body: "
                + errorBody);
      }

      String responseBody = response.body().string();
      InstallationToken token = objectMapper.readValue(responseBody, InstallationToken.class);

      log.debug(
          "Successfully obtained GitHub App installation token, expires at: {}",
          token.getExpiresAt());
      return token;
    }
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

  private boolean isTokenExpired(InstallationToken token) {
    if (token == null || !StringUtils.hasText(token.getExpiresAt())) {
      return true;
    }

    try {
      Instant expiresAt = Instant.parse(token.getExpiresAt());
      Instant refreshThreshold =
          Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(TOKEN_REFRESH_BUFFER_MINUTES));
      return expiresAt.isBefore(refreshThreshold);
    } catch (Exception e) {
      log.warn("Failed to parse token expiration time: {}", token.getExpiresAt(), e);
      return true;
    }
  }

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

  @Data
  private static class InstallationToken {
    @JsonProperty("token")
    private String token;

    @JsonProperty("expires_at")
    private String expiresAt;
  }
}
