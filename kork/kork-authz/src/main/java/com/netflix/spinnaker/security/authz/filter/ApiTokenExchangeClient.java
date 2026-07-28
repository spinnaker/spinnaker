/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.authz.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exchanges an opaque Spinnaker API token ({@code spk_…}) for the signed identity-token JWT Gate
 * would have minted for that principal, by POSTing it to Gate's {@code /auth/apiTokens/exchange}
 * endpoint.
 *
 * <p>Results are cached per token (keyed by SHA-256 hash, never the plaintext) until the lesser of
 * the configured {@code cache-ttl} and the JWT's own {@code exp} (minus a skew margin), so repeated
 * direct calls bearing the same token incur at most one round-trip per validity window. Failed
 * exchanges are negatively cached for a short window to avoid hammering Gate with a bad token.
 */
public class ApiTokenExchangeClient {

  private static final Logger log = LoggerFactory.getLogger(ApiTokenExchangeClient.class);

  private final URI exchangeUri;
  private final Duration readTimeout;
  private final long cacheTtlMillis;
  private final long negativeTtlMillis;
  private final long skewMillis;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Cache<String, CachedExchange> cache;

  public ApiTokenExchangeClient(ApiTokenExchangeProperties properties, String gateUrl) {
    String base = stripTrailingSlash(gateUrl);
    this.exchangeUri = URI.create(base + properties.getExchangePath());
    this.readTimeout = Duration.ofMillis(properties.getReadTimeoutMillis());
    this.cacheTtlMillis = properties.getCacheTtl().toMillis();
    this.negativeTtlMillis =
        Math.min(properties.getNegativeCacheTtl().toMillis(), this.cacheTtlMillis);
    this.skewMillis = properties.getClockSkewSeconds() * 1000L;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
            .build();
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(properties.getCacheMaximumSize())
            .expireAfterWrite(properties.getCacheTtl())
            .build();
  }

  /**
   * Return the identity-token JWT for the given API token, or empty if the token is unknown/expired
   * or Gate could not be reached. Cached per validity window.
   */
  public Optional<String> exchange(String plaintextToken) {
    String key = sha256Hex(plaintextToken);
    long now = System.currentTimeMillis();

    CachedExchange cached = cache.getIfPresent(key);
    if (cached != null && now < cached.expiresAtEpochMs) {
      return Optional.ofNullable(cached.identityToken);
    }

    Optional<String> jwt = doExchange(plaintextToken);
    long expiresAt = jwt.map(token -> computeExpiry(token, now)).orElse(now + negativeTtlMillis);
    cache.put(key, new CachedExchange(jwt.orElse(null), expiresAt));
    return jwt;
  }

  private Optional<String> doExchange(String plaintextToken) {
    try {
      byte[] body = objectMapper.writeValueAsBytes(Map.of("token", plaintextToken));
      HttpRequest request =
          HttpRequest.newBuilder(exchangeUri)
              .timeout(readTimeout)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      int status = response.statusCode();
      if (status == 200) {
        JsonNode node = objectMapper.readTree(response.body());
        JsonNode identityToken = node.get("identityToken");
        if (identityToken != null
            && identityToken.isTextual()
            && !identityToken.asText().isBlank()) {
          return Optional.of(identityToken.asText());
        }
        log.debug("Exchange endpoint returned 200 without an identityToken field");
        return Optional.empty();
      }
      // 401/403/etc — unknown/expired/rejected token. Negatively cached by the caller.
      log.debug("API-token exchange returned HTTP {}", status);
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.debug("API-token exchange call failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Cache until the lesser of the configured TTL ceiling and the JWT's own {@code exp} (minus
   * skew), so we never serve a token the downstream verifier would reject as expired. Falls back to
   * the TTL ceiling when the {@code exp} can't be read (the JWT is not verified here — verification
   * happens downstream).
   */
  private long computeExpiry(String jwt, long now) {
    long ceiling = now + cacheTtlMillis;
    Long exp = readExpEpochMillis(jwt);
    if (exp == null) {
      return ceiling;
    }
    long tokenBound = exp - skewMillis;
    return Math.max(now + 1000L, Math.min(ceiling, tokenBound));
  }

  @Nullable
  private Long readExpEpochMillis(String jwt) {
    try {
      String[] parts = jwt.split("\\.");
      if (parts.length < 2) {
        return null;
      }
      byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
      JsonNode claims = objectMapper.readTree(payload);
      JsonNode exp = claims.get("exp");
      if (exp == null || !exp.canConvertToLong()) {
        return null;
      }
      return exp.asLong() * 1000L;
    } catch (Exception e) {
      return null;
    }
  }

  private static String stripTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static final class CachedExchange {
    @Nullable final String identityToken;
    final long expiresAtEpochMs;

    CachedExchange(@Nullable String identityToken, long expiresAtEpochMs) {
      this.identityToken = identityToken;
      this.expiresAtEpochMs = expiresAtEpochMs;
    }
  }
}
