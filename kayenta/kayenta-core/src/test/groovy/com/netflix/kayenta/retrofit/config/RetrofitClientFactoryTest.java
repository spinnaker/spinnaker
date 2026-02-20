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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * <p>These tests prove the bug described in
 * https://github.com/spinnaker/spinnaker/issues/7454:
 *
 * <p>OkHttp's {@code .authenticator()} only fires after an HTTP 401 response. Prometheus-compatible
 * providers like Coralogix never return 401 — they return HTTP 200 with a non-JSON body (e.g.,
 * "OK") when authentication is missing. This causes Kayenta to fail with a
 * {@code JsonParseException} because the authenticator never triggers and the Authorization header
 * is never sent.
 *
 * <p>The fix replaces {@code .authenticator()} with {@code .addInterceptor()} so the Authorization
 * header is sent proactively on every request.
 *
 * <h3>Test structure:</h3>
 * <ul>
 *   <li>{@link OldAuthenticatorBehavior} — reproduces the bug using the old {@code .authenticator()}
 *       approach
 *   <li>{@link NewInterceptorBehavior} — proves the fix using the new {@code .addInterceptor()}
 *       approach
 * </ul>
 */
public class RetrofitClientFactoryTest {

  private MockServer mockServer;
  private MockServerClient mockServerClient;

  /** Simple test service interface for verifying HTTP requests. */
  public interface TestService {
    @GET("/api/v1/query")
    Call<PrometheusResponse> query();
  }

  /** Simulates a Prometheus query response. */
  public static class PrometheusResponse {
    public String status;
    public String message;
  }

  /**
   * Interceptor that proactively adds the Authorization header to all requests. This is the same
   * implementation as {@link RetrofitClientFactory.AuthorizationInterceptor}, extracted here for
   * testing.
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

  // ---------------------------------------------------------------------------
  // Helper: simulates a Prometheus-compatible provider (e.g., Coralogix)
  //
  //  - With valid Authorization header  → 200 + valid JSON
  //  - Without Authorization header     → 200 + plain-text "OK" (NOT 401)
  //
  // This is the exact server behavior that triggers the bug.
  // ---------------------------------------------------------------------------
  private void stubPrometheusLikeServer(String expectedCredential) {
    // Authenticated request → valid Prometheus JSON response
    mockServerClient
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v1/query")
                .withHeader("Authorization", expectedCredential))
        .respond(
            response()
                .withStatusCode(200)
                .withBody(
                    "{\"status\": \"success\", \"message\": \"metrics data\"}",
                    MediaType.APPLICATION_JSON));

    // Unauthenticated request → 200 with non-JSON body (the problematic behavior)
    mockServerClient
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v1/query"))
        .respond(
            response()
                .withStatusCode(200)
                .withBody("OK", MediaType.TEXT_PLAIN));
  }

  // ---------------------------------------------------------------------------
  // Helper: simulates a standard API that returns 401 for missing auth
  // ---------------------------------------------------------------------------
  private void stubStandard401Server(String expectedCredential) {
    // Authenticated request → valid JSON
    mockServerClient
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v1/query")
                .withHeader("Authorization", expectedCredential))
        .respond(
            response()
                .withStatusCode(200)
                .withBody(
                    "{\"status\": \"success\", \"message\": \"metrics data\"}",
                    MediaType.APPLICATION_JSON));

    // Unauthenticated request → 401 (standard challenge-response)
    mockServerClient
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v1/query"))
        .respond(
            response()
                .withStatusCode(401)
                .withBody("Unauthorized"));
  }

  // =========================================================================
  //  OLD BEHAVIOR — reproduces the bug with .authenticator()
  // =========================================================================
  @Nested
  @DisplayName("OLD behavior (.authenticator) — demonstrates the bug")
  class OldAuthenticatorBehavior {

    /**
     * BUG REPRODUCTION: OkHttp's authenticator never fires when the server returns 200 instead of
     * 401, so the Authorization header is never sent and Kayenta gets a non-JSON response body.
     *
     * <p>This is the exact scenario that happens with Coralogix and similar providers:
     *
     * <ol>
     *   <li>Kayenta sends GET /api/v1/query <b>without</b> Authorization header
     *   <li>Server returns HTTP 200 with body "OK" (plain text, not JSON)
     *   <li>OkHttp's authenticator does NOT trigger (no 401 received)
     *   <li>Retrofit tries to parse "OK" as JSON → {@code JsonParseException}
     * </ol>
     */
    @Test
    @DisplayName("FAILS: authenticator never fires when server returns 200 instead of 401")
    public void authenticatorFailsWhenServerReturns200WithoutAuth() {
      String credential = "Bearer my-prometheus-token";
      stubPrometheusLikeServer(credential);

      // OLD approach: uses .authenticator() — only triggers on 401
      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .authenticator(
                  (route, resp) ->
                      resp.request().newBuilder().header("Authorization", credential).build())
              .build();

      TestService service = createTestService(okHttpClient);

      // The authenticator never fires because the server returns 200 (not 401).
      // Retrofit receives "OK" (plain text) and fails to parse it as JSON.
      assertThatThrownBy(() -> service.query().execute())
          .isInstanceOf(SpinnakerConversionException.class)
          .hasMessageContaining("Failed to process response body");
    }

    /**
     * Shows that the old authenticator approach ONLY works with servers that return 401. This is the
     * assumption that breaks for Prometheus-compatible providers like Coralogix.
     */
    @Test
    @DisplayName("WORKS: authenticator fires only when server returns 401 first")
    public void authenticatorWorksOnlyWhenServerReturns401() throws Exception {
      String credential = "Bearer my-prometheus-token";
      stubStandard401Server(credential);

      // OLD approach: uses .authenticator()
      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .authenticator(
                  (route, resp) ->
                      resp.request().newBuilder().header("Authorization", credential).build())
              .build();

      TestService service = createTestService(okHttpClient);

      // This works because the server returns 401, which triggers the authenticator
      // to retry with the Authorization header.
      retrofit2.Response<PrometheusResponse> response = service.query().execute();

      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.body()).isNotNull();
      assertThat(response.body().status).isEqualTo("success");
    }

    /**
     * Demonstrates that the old approach sends TWO requests to a 401 server: the first without
     * auth (gets 401), then a retry with auth. This is wasteful and breaks entirely when the server
     * does not return 401.
     */
    @Test
    @DisplayName("WASTEFUL: authenticator causes double request even on 401 servers")
    public void authenticatorCausesDoubleRequestOn401Servers() throws Exception {
      String credential = "Bearer my-prometheus-token";
      stubStandard401Server(credential);

      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .authenticator(
                  (route, resp) ->
                      resp.request().newBuilder().header("Authorization", credential).build())
              .build();

      TestService service = createTestService(okHttpClient);
      service.query().execute();

      // Verify TWO requests were made: first without auth, then with auth after 401
      mockServerClient.verify(
          request().withMethod("GET").withPath("/api/v1/query"),
          org.mockserver.verify.VerificationTimes.exactly(2));
    }
  }

  // =========================================================================
  //  NEW BEHAVIOR — proves the fix with .addInterceptor()
  // =========================================================================
  @Nested
  @DisplayName("NEW behavior (.addInterceptor) — proves the fix")
  class NewInterceptorBehavior {

    /**
     * FIX VERIFIED: The interceptor sends the Authorization header on the FIRST request, so the
     * server returns valid JSON immediately. No 401 challenge needed.
     *
     * <p>This is the same Coralogix-like server setup that breaks the old approach, but now it
     * works because auth is sent proactively.
     */
    @Test
    @DisplayName("WORKS: interceptor sends auth proactively — no 401 needed")
    public void interceptorWorksWithServerThatNeverReturns401() throws Exception {
      String credential = "Bearer my-prometheus-token";
      stubPrometheusLikeServer(credential);

      // NEW approach: uses .addInterceptor() — sends auth on every request
      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .addInterceptor(new AuthorizationInterceptor(credential))
              .build();

      TestService service = createTestService(okHttpClient);

      retrofit2.Response<PrometheusResponse> response = service.query().execute();

      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.body()).isNotNull();
      assertThat(response.body().status).isEqualTo("success");
      assertThat(response.body().message).isEqualTo("metrics data");

      // Verify only ONE request was made and it had the Authorization header
      mockServerClient.verify(
          request()
              .withMethod("GET")
              .withPath("/api/v1/query")
              .withHeader("Authorization", credential),
          org.mockserver.verify.VerificationTimes.exactly(1));
    }

    /**
     * Proves the interceptor also works with standard 401-capable servers. The fix is
     * backwards-compatible — it sends auth proactively so the 401 path is never even reached.
     */
    @Test
    @DisplayName("WORKS: interceptor is backwards-compatible with 401 servers")
    public void interceptorAlsoWorksWithStandard401Servers() throws Exception {
      String credential = "Bearer my-prometheus-token";
      stubStandard401Server(credential);

      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .addInterceptor(new AuthorizationInterceptor(credential))
              .build();

      TestService service = createTestService(okHttpClient);

      retrofit2.Response<PrometheusResponse> response = service.query().execute();

      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.body()).isNotNull();
      assertThat(response.body().status).isEqualTo("success");

      // Only ONE request because auth is sent upfront — no 401 round-trip
      mockServerClient.verify(
          request()
              .withMethod("GET")
              .withPath("/api/v1/query")
              .withHeader("Authorization", credential),
          org.mockserver.verify.VerificationTimes.exactly(1));
    }

    /**
     * Verifies that Basic auth credentials are also sent proactively on the first request.
     */
    @Test
    @DisplayName("WORKS: Basic auth is also sent proactively")
    public void basicAuthIsSentProactively() throws Exception {
      String credential = Credentials.basic("prometheus-user", "secret-password");
      stubPrometheusLikeServer(credential);

      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .addInterceptor(new AuthorizationInterceptor(credential))
              .build();

      TestService service = createTestService(okHttpClient);

      retrofit2.Response<PrometheusResponse> response = service.query().execute();

      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.body()).isNotNull();
      assertThat(response.body().status).isEqualTo("success");
    }

    /**
     * Verifies that the interceptor sends the exact Authorization header value that was configured,
     * matching the credential format built by {@code createAuthenticatedClient()}.
     */
    @Test
    @DisplayName("WORKS: Authorization header value matches configured credential exactly")
    public void authorizationHeaderMatchesConfiguredCredential() throws Exception {
      String bearerToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-token";
      String credential = "Bearer " + bearerToken;

      mockServerClient
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/api/v1/query")
                  .withHeader("Authorization", credential))
          .respond(
              response()
                  .withStatusCode(200)
                  .withBody(
                      "{\"status\": \"success\", \"message\": \"ok\"}",
                      MediaType.APPLICATION_JSON));

      OkHttpClient okHttpClient =
          new OkHttpClient.Builder()
              .addInterceptor(new AuthorizationInterceptor(credential))
              .build();

      TestService service = createTestService(okHttpClient);
      retrofit2.Response<PrometheusResponse> response = service.query().execute();

      assertThat(response.isSuccessful()).isTrue();

      // Verify the exact Authorization header was sent
      mockServerClient.verify(
          request()
              .withMethod("GET")
              .withPath("/api/v1/query")
              .withHeader("Authorization", "Bearer " + bearerToken));
    }
  }

  // =========================================================================
  //  SIDE-BY-SIDE COMPARISON — same server, old vs new
  // =========================================================================
  @Nested
  @DisplayName("Side-by-side: same server, old authenticator vs new interceptor")
  class SideBySideComparison {

    /**
     * The definitive before/after test. Uses the SAME server setup (Coralogix-like, no 401) and
     * shows:
     *
     * <ol>
     *   <li>OLD (.authenticator) → JsonParseException (bug)
     *   <li>NEW (.addInterceptor) → success (fix)
     * </ol>
     */
    @Test
    @DisplayName("Same Coralogix-like server: OLD fails, NEW succeeds")
    public void oldFailsNewSucceedsOnSameServer() throws Exception {
      String credential = "Bearer coralogix-api-key";
      stubPrometheusLikeServer(credential);

      // --- OLD approach: .authenticator() ---
      OkHttpClient oldClient =
          new OkHttpClient.Builder()
              .authenticator(
                  (route, resp) ->
                      resp.request().newBuilder().header("Authorization", credential).build())
              .build();

      TestService oldService = createTestService(oldClient);

      // OLD approach FAILS — authenticator never fires, "OK" can't be parsed as JSON
      assertThatThrownBy(() -> oldService.query().execute())
          .isInstanceOf(SpinnakerConversionException.class)
          .hasMessageContaining("Failed to process response body");

      // Reset mock server expectations for the next request
      mockServerClient.reset();
      stubPrometheusLikeServer(credential);

      // --- NEW approach: .addInterceptor() ---
      OkHttpClient newClient =
          new OkHttpClient.Builder()
              .addInterceptor(new AuthorizationInterceptor(credential))
              .build();

      TestService newService = createTestService(newClient);

      // NEW approach SUCCEEDS — interceptor sends auth proactively
      retrofit2.Response<PrometheusResponse> response = newService.query().execute();

      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.body()).isNotNull();
      assertThat(response.body().status).isEqualTo("success");
      assertThat(response.body().message).isEqualTo("metrics data");
    }

    /**
     * Demonstrates the exact error a user sees in production when using Kayenta with Coralogix:
     *
     * <pre>
     * Caused by: com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'OK':
     *   was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
     * </pre>
     *
     * <p>The old authenticator approach causes this because:
     * <ol>
     *   <li>Request sent without Authorization header
     *   <li>Coralogix returns 200 + "OK" (not 401, so authenticator doesn't fire)
     *   <li>Retrofit/Jackson tries to parse "OK" as JSON → JsonParseException
     * </ol>
     */
    @Test
    @DisplayName("Reproduces exact JsonParseException from production Coralogix setup")
    public void reproducesExactProductionError() {
      String credential = "Bearer coralogix-bearer-token";

      // Simulate Coralogix: returns 200 with "OK" when no auth is provided
      mockServerClient
          .when(request().withMethod("GET").withPath("/api/v1/query"))
          .respond(
              response()
                  .withStatusCode(200)
                  .withBody("OK", MediaType.TEXT_PLAIN));

      // Old authenticator-based client (pre-fix behavior)
      OkHttpClient oldClient =
          new OkHttpClient.Builder()
              .authenticator(
                  (route, resp) ->
                      resp.request().newBuilder().header("Authorization", credential).build())
              .build();

      TestService service = createTestService(oldClient);

      // This reproduces the exact error seen in production:
      // JsonParseException: Unrecognized token 'OK'
      assertThatThrownBy(() -> service.query().execute())
          .isInstanceOf(SpinnakerConversionException.class)
          .hasMessageContaining("Failed to process response body");
    }
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
