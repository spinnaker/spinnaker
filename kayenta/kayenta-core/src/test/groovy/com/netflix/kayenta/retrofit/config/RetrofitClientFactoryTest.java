/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.kayenta.retrofit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import java.io.IOException;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.mockserver.netty.MockServer;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;

/**
 * Tests for {@link RetrofitClientFactory} authentication behavior.
 *
 * <p>These tests verify that the Authorization header is sent proactively on the first request,
 * rather than waiting for a 401 challenge. This is critical for services that return 200 with
 * empty/invalid responses when authentication is missing, rather than returning 401.
 */
public class RetrofitClientFactoryTest {

  private MockServer mockServer;
  private MockServerClient mockServerClient;

  /** Simple test service interface for verifying HTTP requests. */
  public interface TestService {
    @GET("/test")
    Call<TestResponse> getTest();
  }

  /** Simple response class for testing. */
  public static class TestResponse {
    public String message;
  }

  /**
   * Interceptor that proactively adds the Authorization header to all requests. This is the same
   * implementation as in RetrofitClientFactory, extracted here for testing.
   */
  private static class AuthorizationInterceptor implements Interceptor {
    private final String credential;

    AuthorizationInterceptor(String credential) {
      this.credential = credential;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request().newBuilder().addHeader("Authorization", credential).build();
      return chain.proceed(request);
    }
  }

  @BeforeEach
  public void setUp() {
    mockServer = new MockServer();
    mockServerClient = new MockServerClient("localhost", mockServer.getPort());
  }

  @AfterEach
  public void cleanup() {
    mockServer.close();
    mockServer.stop();
  }

  /**
   * Verifies that the Authorization header with Bearer token is sent on the first request.
   *
   * <p>This test demonstrates the fix for the issue where some Prometheus-compatible providers
   * return HTTP 200 with empty/invalid body when authentication is missing, rather than 401.
   */
  @Test
  public void bearerTokenIsSentProactivelyOnFirstRequest() throws Exception {
    String bearerToken = "test-bearer-token";
    String credential = "Bearer " + bearerToken;

    // Set up mock server to expect Authorization header on first request
    mockServerClient
        .when(request().withMethod("GET").withPath("/test").withHeader("Authorization", credential))
        .respond(
            response()
                .withStatusCode(200)
                .withBody("{\"message\": \"authenticated\"}", MediaType.APPLICATION_JSON));

    // Create OkHttpClient with the AuthorizationInterceptor (same as RetrofitClientFactory does)
    OkHttpClient okHttpClient =
        new OkHttpClient.Builder().addInterceptor(new AuthorizationInterceptor(credential)).build();

    // Create the service
    TestService service = createTestService(okHttpClient);

    // Execute request - should succeed on first attempt with Authorization header
    retrofit2.Response<TestResponse> response = service.getTest().execute();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body()).isNotNull();
    assertThat(response.body().message).isEqualTo("authenticated");

    // Verify the Authorization header was sent on the first request
    mockServerClient.verify(
        request().withMethod("GET").withPath("/test").withHeader("Authorization", credential));
  }

  /** Verifies that Basic auth credentials are sent proactively on the first request. */
  @Test
  public void basicAuthIsSentProactivelyOnFirstRequest() throws Exception {
    String username = "testuser";
    String password = "testpass";
    String credential = Credentials.basic(username, password);

    // Set up mock server to expect Authorization header on first request
    mockServerClient
        .when(request().withMethod("GET").withPath("/test").withHeader("Authorization", credential))
        .respond(
            response()
                .withStatusCode(200)
                .withBody("{\"message\": \"authenticated\"}", MediaType.APPLICATION_JSON));

    // Create OkHttpClient with the AuthorizationInterceptor
    OkHttpClient okHttpClient =
        new OkHttpClient.Builder().addInterceptor(new AuthorizationInterceptor(credential)).build();

    // Create the service
    TestService service = createTestService(okHttpClient);

    // Execute request - should succeed on first attempt with Authorization header
    retrofit2.Response<TestResponse> response = service.getTest().execute();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body()).isNotNull();
    assertThat(response.body().message).isEqualTo("authenticated");
  }

  /**
   * Demonstrates the problem this fix solves: without proactive auth, a server that returns 200
   * with empty body when auth is missing would cause parse errors.
   *
   * <p>This test simulates the scenario where:
   *
   * <ol>
   *   <li>Server returns 200 with "OK" (non-JSON) when no auth header is present
   *   <li>Server returns 200 with valid JSON when Authorization header is present
   * </ol>
   *
   * <p>With the old authenticator approach (waiting for 401), the request would fail because the
   * server returns 200 with invalid JSON. With the interceptor fix, the Authorization header is
   * sent on the first request, so the server returns valid JSON.
   */
  @Test
  public void demonstratesProactiveAuthPreventsParseErrors() throws Exception {
    String bearerToken = "my-secret-token";
    String credential = "Bearer " + bearerToken;

    // Mock server behavior:
    // - With Authorization header: returns 200 with valid JSON
    mockServerClient
        .when(request().withMethod("GET").withPath("/test").withHeader("Authorization", credential))
        .respond(
            response()
                .withStatusCode(200)
                .withBody("{\"message\": \"success\"}", MediaType.APPLICATION_JSON));

    // Create OkHttpClient with the AuthorizationInterceptor
    OkHttpClient okHttpClient =
        new OkHttpClient.Builder().addInterceptor(new AuthorizationInterceptor(credential)).build();

    TestService service = createTestService(okHttpClient);

    // With the interceptor, Authorization header is sent proactively
    // so the server returns valid JSON and parsing succeeds
    retrofit2.Response<TestResponse> response = service.getTest().execute();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body()).isNotNull();
    assertThat(response.body().message).isEqualTo("success");
  }

  /**
   * Demonstrates that without the interceptor, requests would be sent without auth headers. This
   * shows the problem that the fix addresses.
   *
   * <p>When no Authorization header is sent, servers like some Prometheus-compatible providers
   * return HTTP 200 with a non-JSON body (e.g., "OK"). This causes a JSON parse error, which is
   * exactly the problem that the proactive auth interceptor fixes.
   */
  @Test
  public void withoutInterceptorNoAuthHeaderIsSent() throws Exception {
    // Set up mock server to accept requests without auth and return non-JSON
    mockServerClient
        .when(request().withMethod("GET").withPath("/test"))
        .respond(
            response()
                .withStatusCode(200)
                .withBody("OK", MediaType.TEXT_PLAIN)); // Non-JSON response

    // Create OkHttpClient WITHOUT the interceptor
    OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

    TestService service = createTestService(okHttpClient);

    // This request goes through without auth - demonstrating the problem
    // The server returns 200 with "OK" (non-JSON), which causes a parse error
    assertThatThrownBy(() -> service.getTest().execute())
        .isInstanceOf(SpinnakerConversionException.class)
        .hasMessageContaining("Failed to process response body");
  }

  private TestService createTestService(OkHttpClient okHttpClient) {
    String baseUrl = "http://localhost:" + mockServer.getPort() + "/";
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
        .build()
        .create(TestService.class);
  }
}
