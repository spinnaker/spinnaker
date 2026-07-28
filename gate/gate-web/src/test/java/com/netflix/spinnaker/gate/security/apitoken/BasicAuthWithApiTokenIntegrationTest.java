/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.security.apitoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPool;

/**
 * Integration test that API token auth coexists with basic-auth / form-login. The dedicated token
 * chain inserts {@link ApiTokenAuthenticationFilter} before {@link
 * org.springframework.security.web.authentication.www.BasicAuthenticationFilter}, so a valid token
 * populates the {@link org.springframework.security.core.context.SecurityContext} before any /login
 * redirect can fire.
 */
@SpringBootTest(
    classes = {Main.class, BasicAuthWithApiTokenIntegrationTest.TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "security.basicform.enabled=true",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "api-tokens.enabled=true",
    })
class BasicAuthWithApiTokenIntegrationTest {

  private static final String VALID_TOKEN = "spk_integration_test_token_abc123";

  @Autowired TestRestTemplate restTemplate;

  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;
  @MockitoBean ApplicationService applicationService;
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;

  /** Prevents real Redis connections required by {@link RedisApiTokenRepository}. */
  @MockitoBean JedisPool jedisPool;

  /** Mocked so tests control which tokens resolve, without a real Redis store. */
  @MockitoBean ApiTokenService apiTokenService;

  @BeforeEach
  void setup() {
    TokenRecord record = new TokenRecord();
    record.setId("test-token-id");
    record.setPrincipalId("token-user@example.com");
    record.setPrincipalType("USER");
    when(apiTokenService.resolveByHash(ApiTokenHashing.sha256Hex(VALID_TOKEN)))
        .thenReturn(Optional.of(record));
  }

  /** Negative control: same wiring, no token → still redirected to /login. */
  @Test
  @DisplayName(
      "negative control — without a token header, unauthenticated request still redirects to"
          + " /login (so the 200 in validTokenRequestSucceeds is attributable to the token filter)")
  void unauthenticatedRequestRedirects() {
    var response =
        restTemplate
            .withRequestFactorySettings(
                new ClientHttpRequestFactorySettings(
                    ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW,
                    Duration.ofMillis(500),
                    Duration.ofMillis(500),
                    null))
            .exchange("/hello", HttpMethod.GET, null, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/login");
  }

  @Test
  @DisplayName(
      "request with a valid X-Spinnaker-Token returns 200 — not a redirect to /login (the fix)")
  void validTokenRequestSucceeds() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, VALID_TOKEN);

    var response =
        restTemplate.exchange("/hello", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("request with a valid Authorization: Bearer token returns 200")
  void bearerTokenRequestSucceeds() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + VALID_TOKEN);

    var response =
        restTemplate.exchange("/hello", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName(
      "request with an unrecognised token returns 401 — the dedicated token chain rejects"
          + " API-style auth without redirecting to /login")
  void unknownTokenReturns401() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, "spk_not_a_real_token");

    var response =
        restTemplate.exchange("/hello", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName(
      "token-authed request does NOT set a JSESSIONID cookie — dedicated STATELESS chain prevents"
          + " token auth from leaking into a session")
  void tokenAuthIsStateless() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, VALID_TOKEN);

    var response =
        restTemplate.exchange("/hello", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    java.util.List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    if (setCookies != null) {
      assertThat(setCookies)
          .as("token-authed response must not create a JSESSIONID cookie")
          .noneMatch(c -> c.toUpperCase(java.util.Locale.ROOT).startsWith("JSESSIONID="));
    }
  }

  @Configuration
  static class TestConfiguration {

    @RestController
    static class HelloController {

      @GetMapping("/hello")
      String hello() {
        return "hello";
      }
    }
  }
}
