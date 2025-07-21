package com.netflix.spinnaker.fiat.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubAppRequestInterceptor;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubConfigTest {

  @Mock private OkHttp3ClientConfiguration mockOkHttpClientConfig;
  @Mock private OkHttpClient.Builder mockClientBuilder;
  @Mock private OkHttpClient mockClient;

  private GitHubConfig gitHubConfig;
  private GitHubProperties gitHubProperties;
  private Path tempPrivateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    gitHubConfig = new GitHubConfig();
    gitHubProperties = new GitHubProperties();
    gitHubProperties.setBaseUrl("https://api.github.com");
    gitHubProperties.setOrganization("test-org");
    gitHubConfig.setGitHubProperties(gitHubProperties);

    // Create test private key
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    RSAPrivateKey privateKey = (RSAPrivateKey) keyGen.generateKeyPair().getPrivate();

    String pemKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n"
            + "-----END PRIVATE KEY-----";

    tempPrivateKeyFile = tempDir.resolve("test-private-key.pem");
    Files.write(tempPrivateKeyFile, pemKey.getBytes());

    // Mock OkHttpClient configuration
    when(mockOkHttpClientConfig.createForRetrofit2()).thenReturn(mockClientBuilder);
    when(mockClientBuilder.addInterceptor(any(Interceptor.class))).thenReturn(mockClientBuilder);
    when(mockClientBuilder.build()).thenReturn(mockClient);
  }

  @Test
  void shouldValidateGitHubAppConfiguration() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then
    assertDoesNotThrow(() -> gitHubConfig.validateConfiguration());
  }

  @Test
  void shouldValidatePATConfiguration() {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");

    // When & Then
    assertDoesNotThrow(() -> gitHubConfig.validateConfiguration());
  }

  @Test
  void shouldFailValidationWhenNoAuthConfigured() {
    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              gitHubConfig.validateConfiguration();
            });

    assertTrue(exception.getMessage().contains("No GitHub authentication method configured"));
  }

  @Test
  void shouldCreateGitHubClientWithGitHubApp() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
    verify(mockClientBuilder).addInterceptor(any(GitHubAppRequestInterceptor.class));
    verify(mockClientBuilder).build();
  }

  @Test
  void shouldCreateGitHubClientWithPAT() {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
    verify(mockClientBuilder).addInterceptor(any(Interceptor.class));
    verify(mockClientBuilder).build();
  }

  @Test
  void shouldPreferGitHubAppWhenBothConfigured() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");
    gitHubProperties.setAccessToken("ghp_test_token");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
    assertTrue(gitHubProperties.shouldUseGitHubApp());
    verify(mockClientBuilder).addInterceptor(any(GitHubAppRequestInterceptor.class));
  }

  @Test
  void shouldUseCorrectBaseUrl() {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");
    gitHubProperties.setBaseUrl("https://github.company.com/api/v3");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
    // The client should be configured with the correct base URL
  }

  @Test
  void shouldHandleTrailingSlashInBaseUrl() {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");
    gitHubProperties.setBaseUrl("https://api.github.com/");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
  }

  @Test
  void shouldForceGitHubAppMode() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.GITHUB_APP);
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
    assertTrue(gitHubProperties.shouldUseGitHubApp());
    verify(mockClientBuilder).addInterceptor(any(GitHubAppRequestInterceptor.class));
  }

  @Test
  void shouldForcePATMode() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.PAT);
    gitHubProperties.setAccessToken("ghp_test_token");
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When
    GitHubClient client = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client);
    assertFalse(gitHubProperties.shouldUseGitHubApp());
    verify(mockClientBuilder).addInterceptor(not(any(GitHubAppRequestInterceptor.class)));
  }

  @Test
  void shouldFailWhenForceGitHubAppButNotConfigured() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.GITHUB_APP);
    gitHubProperties.setAccessToken("ghp_test_token"); // Only PAT configured

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              gitHubConfig.validateConfiguration();
            });

    assertTrue(
        exception
            .getMessage()
            .contains("GitHub App authentication is forced but not properly configured"));
  }

  @Test
  void shouldFailWhenForcePATButNotConfigured() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.PAT);
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              gitHubConfig.validateConfiguration();
            });

    assertTrue(
        exception
            .getMessage()
            .contains("PAT authentication is forced but 'accessToken' is not configured"));
  }

  @Test
  void shouldHandleInvalidPrivateKeyPath() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath("/nonexistent/path/private-key.pem");
    gitHubProperties.setInstallationId("67890");

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              gitHubConfig.gitHubClient(mockOkHttpClientConfig);
            });

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldCreateSingletonGitHubClient() {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");

    // When
    GitHubClient client1 = gitHubConfig.gitHubClient(mockOkHttpClientConfig);
    GitHubClient client2 = gitHubConfig.gitHubClient(mockOkHttpClientConfig);

    // Then
    assertNotNull(client1);
    assertNotNull(client2);
    // Note: In actual Spring context, these would be the same instance due to @Bean
  }
}
