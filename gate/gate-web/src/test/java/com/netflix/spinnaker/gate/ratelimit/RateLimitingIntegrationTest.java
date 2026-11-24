/*
 * Copyright 2026 Spinnaker Contributors
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
package com.netflix.spinnaker.gate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenAuthenticationFilter;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenHashing;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenService;
import com.netflix.spinnaker.gate.security.apitoken.TokenRecord;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Runs the rate limiter inside gate's real security filter chain (dedicated API token chain plus
 * basic/form login) against a real redis, to prove it only ever sees authenticated requests and
 * limits them per authenticated principal.
 *
 * <p>OAuth2, SAML and the other mechanisms all leave a kork {@code User} behind in the {@code
 * SecurityContext}; {@link RateLimitingFilterTest} covers each of those shapes, while this test
 * covers the filter ordering relative to the real chains.
 */
@SpringBootTest(
    classes = {Main.class, RateLimitingIntegrationTest.TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "security.basicform.enabled=true",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "api-tokens.enabled=true",
      "rate-limit.enabled=true",
      "rate-limit.learning=false",
      "rate-limit.capacity=2",
      "rate-limit.rate-seconds=60",
      "rate-limit.rules[0].name=limited",
      "rate-limit.rules[0].path-patterns[0]=/limited",
      "rate-limit.rules[0].capacity=1",
      "rate-limit.rules[0].rate-seconds=60",
    })
class RateLimitingIntegrationTest {

  private static final String TOKEN = "spk_rate_limit_test_token";
  private static final String OTHER_TOKEN = "spk_rate_limit_other_token";

  static final EmbeddedRedis embeddedRedis = EmbeddedRedis.embed();

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) {
    registry.add(
        "redis.connection",
        () -> "redis://" + embeddedRedis.getHost() + ":" + embeddedRedis.getPort());
  }

  @AfterAll
  static void stopRedis() {
    embeddedRedis.destroy();
  }

  // The JDK client never retries. TestRestTemplate's apache client honors Retry-After on a 429 by
  // sleeping and retrying, which would mask the very response these tests assert on.
  private final HttpClient client =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @LocalServerPort int port;
  @Autowired JedisPool jedisPool;

  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;
  @MockitoBean ApplicationService applicationService;
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;
  @MockitoBean ApiTokenService apiTokenService;

  @BeforeEach
  void setup() {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.flushDB();
    }
    when(apiTokenService.resolveByHash(ApiTokenHashing.sha256Hex(TOKEN)))
        .thenReturn(Optional.of(record("token-id", "token-user@example.com")));
    when(apiTokenService.resolveByHash(ApiTokenHashing.sha256Hex(OTHER_TOKEN)))
        .thenReturn(Optional.of(record("other-id", "other-user@example.com")));
  }

  static TokenRecord record(String id, String principal) {
    TokenRecord record = new TokenRecord();
    record.setId(id);
    record.setPrincipalId(principal);
    record.setPrincipalType("USER");
    return record;
  }

  Set<String> rateLimitKeys() {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.keys("rateLimit*");
    }
  }

  HttpResponse<String> get(String path, String headerName, String headerValue) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    if (headerName != null) {
      request.header(headerName, headerValue);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> getWithToken(String path, String token) throws Exception {
    return get(path, ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, token);
  }

  @Test
  @DisplayName("api token requests are limited per token principal and rejected with a 429")
  void apiTokenRequestsAreLimitedPerPrincipal() throws Exception {
    var first = getWithToken("/hello", TOKEN);
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.headers().firstValue("X-RateLimit-Capacity")).contains("2");
    assertThat(first.headers().firstValue("X-RateLimit-Remaining")).contains("1");
    assertThat(first.headers().firstValue("X-RateLimit-Learning")).contains("false");

    assertThat(getWithToken("/hello", TOKEN).statusCode()).isEqualTo(200);

    var rejected = getWithToken("/hello", TOKEN);
    assertThat(rejected.statusCode()).isEqualTo(429);
    assertThat(rejected.headers().firstValue("Retry-After")).isPresent();
    assertThat(rejected.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/json");
    assertThat(rejected.body()).contains("Rate capacity exceeded");

    assertThat(rateLimitKeys()).containsExactly("rateLimit:token-user@example.com");

    // another principal is unaffected
    assertThat(getWithToken("/hello", OTHER_TOKEN).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("rule limits apply to the matching endpoint through the real chain")
  void ruleLimitsApplyToMatchingEndpoints() throws Exception {
    var first = getWithToken("/limited", TOKEN);
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.headers().firstValue("X-RateLimit-Rule")).contains("limited");
    assertThat(first.headers().firstValue("X-RateLimit-Capacity")).contains("1");

    assertThat(getWithToken("/limited", TOKEN).statusCode()).isEqualTo(429);

    // the default bucket for the same principal is untouched
    var hello = getWithToken("/hello", TOKEN);
    assertThat(hello.statusCode()).isEqualTo(200);
    assertThat(hello.headers().firstValue("X-RateLimit-Remaining")).contains("1");
  }

  @Test
  @DisplayName("unknown tokens are rejected by security before the limiter ever counts them")
  void unknownTokensAreNotCounted() throws Exception {
    for (int i = 0; i < 5; i++) {
      assertThat(getWithToken("/hello", "spk_not_a_real_token_" + i).statusCode()).isEqualTo(401);
    }

    assertThat(rateLimitKeys()).isEmpty();
  }

  @Test
  @DisplayName("unauthenticated requests are redirected to login before the limiter counts them")
  void unauthenticatedRequestsAreNotCounted() throws Exception {
    for (int i = 0; i < 5; i++) {
      var response = get("/hello", null, null);
      assertThat(response.statusCode()).isEqualTo(302);
      assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/login");
    }

    assertThat(rateLimitKeys()).isEmpty();
  }

  @Test
  @DisplayName("basic authenticated users are limited per username")
  void basicAuthUsersAreLimited() throws Exception {
    String basic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("testuser:testpassword".getBytes(StandardCharsets.UTF_8));

    assertThat(get("/hello", "Authorization", basic).statusCode()).isEqualTo(200);
    assertThat(get("/hello", "Authorization", basic).statusCode()).isEqualTo(200);
    assertThat(get("/hello", "Authorization", basic).statusCode()).isEqualTo(429);
    assertThat(rateLimitKeys()).containsExactly("rateLimit:testuser");
  }

  @Configuration
  static class TestConfiguration {

    @RestController
    static class Controller {

      @GetMapping("/hello")
      String hello() {
        return "hello";
      }

      @GetMapping("/limited")
      String limited() {
        return "limited";
      }
    }
  }
}
