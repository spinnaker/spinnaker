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

package com.netflix.spinnaker.fiat.roles.github.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubAppRequestInterceptorTest {

  @Mock private GitHubAppAuthService mockAuthService;

  private GitHubAppRequestInterceptor interceptor;
  private Request testRequest;

  @BeforeEach
  void setUp() {
    interceptor = new GitHubAppRequestInterceptor(mockAuthService);
    testRequest = new Request.Builder().url("https://api.github.com/test").build();
  }

  @Test
  void shouldAddAuthorizationHeaderToRequest() throws IOException {
    // Given
    String testToken = "ghs_test_installation_token";
    when(mockAuthService.getInstallationToken()).thenReturn(testToken);

    TestChain chain = new TestChain(testRequest);

    // When
    interceptor.intercept(chain);

    // Then
    Request modifiedRequest = chain.getCapturedRequest();
    assertNotNull(modifiedRequest);
    assertEquals("Bearer " + testToken, modifiedRequest.header("Authorization"));
    assertEquals("application/vnd.github.v3+json", modifiedRequest.header("Accept"));
    assertEquals("Spinnaker-Fiat", modifiedRequest.header("User-Agent"));
    verify(mockAuthService).getInstallationToken();
  }

  @Test
  void shouldHandleAuthServiceFailure() throws IOException {
    // Given
    IOException authException = new IOException("Failed to get installation token");
    when(mockAuthService.getInstallationToken()).thenThrow(authException);

    TestChain chain = new TestChain(testRequest);

    // When & Then
    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              interceptor.intercept(chain);
            });

    assertEquals("GitHub App authentication failed", exception.getMessage());
    assertEquals(authException, exception.getCause());
    verify(mockAuthService).getInstallationToken();
    assertFalse(chain.wasProceededCalled());
  }

  @Test
  void shouldHandleRuntimeExceptionFromAuthService() throws IOException {
    // Given
    RuntimeException authException = new RuntimeException("Configuration error");
    when(mockAuthService.getInstallationToken()).thenThrow(authException);

    TestChain chain = new TestChain(testRequest);

    // When & Then
    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              interceptor.intercept(chain);
            });

    assertEquals("GitHub App authentication failed", exception.getMessage());
    assertEquals(authException, exception.getCause());
    verify(mockAuthService).getInstallationToken();
    assertFalse(chain.wasProceededCalled());
  }

  @Test
  void shouldProceedWithModifiedRequest() throws IOException {
    // Given
    String testToken = "ghs_another_token";
    when(mockAuthService.getInstallationToken()).thenReturn(testToken);

    TestChain chain = new TestChain(testRequest);

    // When
    interceptor.intercept(chain);

    // Then
    Request modifiedRequest = chain.getCapturedRequest();
    assertTrue(chain.wasProceededCalled());

    // Verify the request was modified with all required headers
    assertEquals("Bearer " + testToken, modifiedRequest.header("Authorization"));
    assertEquals("application/vnd.github.v3+json", modifiedRequest.header("Accept"));
    assertEquals("Spinnaker-Fiat", modifiedRequest.header("User-Agent"));
  }

  @Test
  void shouldCallAuthServiceOnlyOnce() throws IOException {
    // Given
    String testToken = "ghs_single_call_token";
    when(mockAuthService.getInstallationToken()).thenReturn(testToken);

    TestChain chain = new TestChain(testRequest);

    // When
    interceptor.intercept(chain);

    // Then
    verify(mockAuthService, times(1)).getInstallationToken();
  }

  /** Test helper class to capture intercepted requests without mocking final classes */
  private static class TestChain implements Interceptor.Chain {
    private final Request originalRequest;
    private Request capturedRequest;
    private boolean proceededCalled = false;

    TestChain(Request request) {
      this.originalRequest = request;
    }

    @Override
    public Request request() {
      return originalRequest;
    }

    @Override
    public Response proceed(Request request) {
      this.capturedRequest = request;
      this.proceededCalled = true;
      // Return a minimal response
      return new Response.Builder()
          .request(request)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(ResponseBody.create("", MediaType.get("application/json")))
          .build();
    }

    @Override
    public Connection connection() {
      return null;
    }

    @Override
    public Call call() {
      return null;
    }

    @Override
    public int connectTimeoutMillis() {
      return 0;
    }

    @Override
    public Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
      return this;
    }

    @Override
    public int readTimeoutMillis() {
      return 0;
    }

    @Override
    public Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
      return this;
    }

    @Override
    public int writeTimeoutMillis() {
      return 0;
    }

    @Override
    public Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
      return this;
    }

    Request getCapturedRequest() {
      return capturedRequest;
    }

    boolean wasProceededCalled() {
      return proceededCalled;
    }
  }
}
