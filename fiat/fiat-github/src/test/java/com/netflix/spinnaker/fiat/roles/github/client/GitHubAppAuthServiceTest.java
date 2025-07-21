package com.netflix.spinnaker.fiat.roles.github.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubAppAuthServiceTest {

  @Mock private OkHttpClient mockHttpClient;
  @Mock private Call mockCall;
  @Mock private Response mockResponse;
  @Mock private ResponseBody mockResponseBody;

  private GitHubAppAuthService authService;
  private String testAppId = "12345";
  private String testInstallationId = "67890";
  private Path tempPrivateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    // Generate a test RSA private key
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    RSAPrivateKey privateKey = (RSAPrivateKey) keyGen.generateKeyPair().getPrivate();

    // Create PEM format private key
    String pemKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n"
            + "-----END PRIVATE KEY-----";

    tempPrivateKeyFile = tempDir.resolve("test-private-key.pem");
    Files.write(tempPrivateKeyFile, pemKey.getBytes());

    authService =
        new GitHubAppAuthService(
            testAppId,
            tempPrivateKeyFile.toString(),
            testInstallationId,
            "https://api.github.com",
            mockHttpClient);
  }

  @Test
  void shouldGenerateInstallationToken() throws IOException {
    // Given
    String mockTokenResponse =
        "{\"token\":\"ghs_test_token\",\"expires_at\":\""
            + Instant.now().plusSeconds(3600).toString()
            + "\"}";

    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body()).thenReturn(mockResponseBody);
    when(mockResponseBody.string()).thenReturn(mockTokenResponse);

    // When
    String token = authService.getInstallationToken();

    // Then
    assertEquals("ghs_test_token", token);

    // Verify the request was made correctly
    verify(mockHttpClient)
        .newCall(
            argThat(
                request -> {
                  assertEquals("POST", request.method());
                  assertTrue(
                      request
                          .url()
                          .toString()
                          .contains("/app/installations/" + testInstallationId + "/access_tokens"));
                  assertNotNull(request.header("Authorization"));
                  assertTrue(request.header("Authorization").startsWith("Bearer "));
                  assertEquals("application/vnd.github.v3+json", request.header("Accept"));
                  assertEquals("Spinnaker-Fiat", request.header("User-Agent"));
                  return true;
                }));
  }

  @Test
  void shouldCacheInstallationToken() throws IOException {
    // Given
    String futureExpiry = Instant.now().plusSeconds(3600).toString();
    String mockTokenResponse =
        "{\"token\":\"ghs_cached_token\",\"expires_at\":\"" + futureExpiry + "\"}";

    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body()).thenReturn(mockResponseBody);
    when(mockResponseBody.string()).thenReturn(mockTokenResponse);

    // When
    String token1 = authService.getInstallationToken();
    String token2 = authService.getInstallationToken();

    // Then
    assertEquals("ghs_cached_token", token1);
    assertEquals("ghs_cached_token", token2);
    verify(mockHttpClient, times(1)).newCall(any(Request.class)); // Only called once due to caching
  }

  @Test
  void shouldRefreshExpiredToken() throws IOException {
    // Given - expired token
    String expiredTime = Instant.now().minusSeconds(3600).toString();
    String expiredTokenResponse =
        "{\"token\":\"ghs_expired_token\",\"expires_at\":\"" + expiredTime + "\"}";

    // Fresh token
    String futureTime = Instant.now().plusSeconds(3600).toString();
    String freshTokenResponse =
        "{\"token\":\"ghs_fresh_token\",\"expires_at\":\"" + futureTime + "\"}";

    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body()).thenReturn(mockResponseBody);
    when(mockResponseBody.string()).thenReturn(expiredTokenResponse, freshTokenResponse);

    // When
    String token1 = authService.getInstallationToken(); // Gets expired token
    String token2 = authService.getInstallationToken(); // Should refresh and get fresh token

    // Then
    assertEquals("ghs_expired_token", token1);
    assertEquals("ghs_fresh_token", token2);
    verify(mockHttpClient, times(2)).newCall(any(Request.class)); // Called twice due to refresh
  }

  @Test
  void shouldHandleTokenRequestFailure() throws IOException {
    // Given
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(false);
    when(mockResponse.code()).thenReturn(401);
    when(mockResponse.body()).thenReturn(mockResponseBody);
    when(mockResponseBody.string()).thenReturn("{\"message\":\"Bad credentials\"}");

    // When & Then
    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              authService.getInstallationToken();
            });

    assertTrue(exception.getMessage().contains("Failed to get installation token"));
    assertTrue(exception.getMessage().contains("401"));
  }

  @Test
  void shouldHandleInvalidPrivateKeyFile() {
    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              new GitHubAppAuthService(
                  testAppId,
                  "/nonexistent/path/private-key.pem",
                  testInstallationId,
                  "https://api.github.com",
                  mockHttpClient);
            });

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldHandleInvalidTokenResponse() throws IOException {
    // Given - invalid JSON response
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body()).thenReturn(mockResponseBody);
    when(mockResponseBody.string()).thenReturn("invalid json");

    // When & Then
    assertThrows(
        Exception.class,
        () -> {
          authService.getInstallationToken();
        });
  }

  @Test
  void shouldRefreshTokenBeforeExpiry() throws IOException {
    // Given - token expiring soon (within refresh buffer)
    String soonToExpire =
        Instant.now().plusSeconds(240).toString(); // 4 minutes from now (< 5 min buffer)
    String expiringTokenResponse =
        "{\"token\":\"ghs_expiring_token\",\"expires_at\":\"" + soonToExpire + "\"}";

    String futureTime = Instant.now().plusSeconds(3600).toString();
    String freshTokenResponse =
        "{\"token\":\"ghs_refreshed_token\",\"expires_at\":\"" + futureTime + "\"}";

    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body()).thenReturn(mockResponseBody);
    when(mockResponseBody.string()).thenReturn(expiringTokenResponse, freshTokenResponse);

    // When
    String token1 = authService.getInstallationToken(); // Gets expiring token
    String token2 = authService.getInstallationToken(); // Should refresh due to expiry buffer

    // Then
    assertEquals("ghs_expiring_token", token1);
    assertEquals("ghs_refreshed_token", token2);
    verify(mockHttpClient, times(2)).newCall(any(Request.class));
  }
}
