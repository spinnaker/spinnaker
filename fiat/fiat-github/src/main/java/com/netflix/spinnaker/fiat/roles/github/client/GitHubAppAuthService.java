package com.netflix.spinnaker.fiat.roles.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
    try {
      String content = new String(Files.readAllBytes(Paths.get(privateKeyPath)));

      // Remove PEM headers and footers, and whitespace
      String privateKeyPEM =
          content
              .replaceAll("-----BEGIN PRIVATE KEY-----", "")
              .replaceAll("-----END PRIVATE KEY-----", "")
              .replaceAll("-----BEGIN RSA PRIVATE KEY-----", "")
              .replaceAll("-----END RSA PRIVATE KEY-----", "")
              .replaceAll("\\s+", "");

      byte[] keyBytes = Base64.getDecoder().decode(privateKeyPEM);
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");

      return keyFactory.generatePrivate(spec);
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
