package com.netflix.spinnaker.fiat.roles.github.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubAppRequestInterceptorTest {

  @Mock private GitHubAppAuthService mockAuthService;
  @Mock private Interceptor.Chain mockChain;
  @Mock private Request mockRequest;
  @Mock private Request.Builder mockRequestBuilder;
  @Mock private Response mockResponse;

  private GitHubAppRequestInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new GitHubAppRequestInterceptor(mockAuthService);
  }

  @Test
  void shouldAddAuthorizationHeaderToRequest() throws IOException {
    // Given
    String testToken = "ghs_test_installation_token";
    when(mockAuthService.getInstallationToken()).thenReturn(testToken);
    when(mockChain.request()).thenReturn(mockRequest);
    when(mockRequest.newBuilder()).thenReturn(mockRequestBuilder);
    when(mockRequestBuilder.addHeader(anyString(), anyString())).thenReturn(mockRequestBuilder);
    when(mockRequestBuilder.build()).thenReturn(mockRequest);
    when(mockChain.proceed(mockRequest)).thenReturn(mockResponse);

    // When
    Response response = interceptor.intercept(mockChain);

    // Then
    assertEquals(mockResponse, response);
    verify(mockAuthService).getInstallationToken();
    verify(mockRequestBuilder).addHeader("Authorization", "Bearer " + testToken);
    verify(mockRequestBuilder).addHeader("Accept", "application/vnd.github.v3+json");
    verify(mockRequestBuilder).addHeader("User-Agent", "Spinnaker-Fiat");
    verify(mockChain).proceed(mockRequest);
  }

  @Test
  void shouldHandleAuthServiceFailure() throws IOException {
    // Given
    IOException authException = new IOException("Failed to get installation token");
    when(mockAuthService.getInstallationToken()).thenThrow(authException);
    when(mockChain.request()).thenReturn(mockRequest);

    // When & Then
    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              interceptor.intercept(mockChain);
            });

    assertEquals("GitHub App authentication failed", exception.getMessage());
    assertEquals(authException, exception.getCause());
    verify(mockAuthService).getInstallationToken();
    verify(mockChain, never()).proceed(any());
  }

  @Test
  void shouldHandleRuntimeExceptionFromAuthService() throws IOException {
    // Given
    RuntimeException authException = new RuntimeException("Configuration error");
    when(mockAuthService.getInstallationToken()).thenThrow(authException);
    when(mockChain.request()).thenReturn(mockRequest);

    // When & Then
    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              interceptor.intercept(mockChain);
            });

    assertEquals("GitHub App authentication failed", exception.getMessage());
    assertEquals(authException, exception.getCause());
    verify(mockAuthService).getInstallationToken();
    verify(mockChain, never()).proceed(any());
  }

  @Test
  void shouldProceedWithModifiedRequest() throws IOException {
    // Given
    String testToken = "ghs_another_token";
    when(mockAuthService.getInstallationToken()).thenReturn(testToken);
    when(mockChain.request()).thenReturn(mockRequest);
    when(mockRequest.newBuilder()).thenReturn(mockRequestBuilder);
    when(mockRequestBuilder.addHeader(anyString(), anyString())).thenReturn(mockRequestBuilder);
    when(mockRequestBuilder.build()).thenReturn(mockRequest);
    when(mockChain.proceed(mockRequest)).thenReturn(mockResponse);

    // When
    Response response = interceptor.intercept(mockChain);

    // Then
    assertEquals(mockResponse, response);

    // Verify the request was modified with all required headers
    verify(mockRequestBuilder).addHeader("Authorization", "Bearer " + testToken);
    verify(mockRequestBuilder).addHeader("Accept", "application/vnd.github.v3+json");
    verify(mockRequestBuilder).addHeader("User-Agent", "Spinnaker-Fiat");
    verify(mockRequestBuilder).build();
    verify(mockChain).proceed(mockRequest);
  }

  @Test
  void shouldCallAuthServiceOnlyOnce() throws IOException {
    // Given
    String testToken = "ghs_single_call_token";
    when(mockAuthService.getInstallationToken()).thenReturn(testToken);
    when(mockChain.request()).thenReturn(mockRequest);
    when(mockRequest.newBuilder()).thenReturn(mockRequestBuilder);
    when(mockRequestBuilder.addHeader(anyString(), anyString())).thenReturn(mockRequestBuilder);
    when(mockRequestBuilder.build()).thenReturn(mockRequest);
    when(mockChain.proceed(mockRequest)).thenReturn(mockResponse);

    // When
    interceptor.intercept(mockChain);

    // Then
    verify(mockAuthService, times(1)).getInstallationToken();
  }
}
