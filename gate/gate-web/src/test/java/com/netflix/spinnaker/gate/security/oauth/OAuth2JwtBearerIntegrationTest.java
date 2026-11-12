/*
 * Copyright 2026 Spinnaker.io
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

package com.netflix.spinnaker.gate.security.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.security.oauth.config.OAuth2TestConfiguration;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Integration test for bearer tokens validated as JWTs
 * (security.oauth2.external-auth-token-filter.jwt.*), with the user-info fallback off.
 *
 * <p>WireMock serves the issuer's signing keys and also a user-info endpoint that would accept any
 * token, so the tests show that only JWTs issued for this Gate get in.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.security.oauth2.client.registration.github.client-id=client-id",
      "spring.security.oauth2.client.registration.github.client-secret=client-secret",
      "spring.security.oauth2.client.registration.github.scope=user,email",
      "spring.security.oauth2.client.registration.userInfoMapping.email=email",
      "spring.security.oauth2.client.registration.userInfoMapping.username=preferred_username",
      "security.oauth2.external-auth-token-filter.jwt.issuer-uri="
          + OAuth2JwtBearerIntegrationTest.ISSUER,
      "security.oauth2.external-auth-token-filter.jwt.audiences="
          + OAuth2JwtBearerIntegrationTest.AUDIENCE,
      "security.oauth2.external-auth-token-filter.jwt.required-scopes=access",
      "security.oauth2.external-auth-token-filter.jwt.user-info-fallback=false"
    })
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
@Import(OAuth2TestConfiguration.class)
class OAuth2JwtBearerIntegrationTest {

  static final String ISSUER = "https://login.example.com/tenant/v2.0";
  static final String AUDIENCE = "gate-api";

  private static RSAKey signingKey;

  @RegisterExtension
  static WireMockExtension provider =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @LocalServerPort private int appPort;

  /** To prevent attempts to connect to clouddriver */
  @MockitoBean private DefaultProviderLookupService defaultProviderLookupService;

  /** To prevent attempts to connect to front50 */
  @MockitoBean private ApplicationService applicationService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockitoBean private DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** Doesn't follow redirects, so an unauthenticated request shows up as the 302 to the login. */
  private final RestTemplate noRedirects = noRedirectRestTemplate();

  @BeforeAll
  static void createSigningKey() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
  }

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    String base = "http://localhost:" + provider.getPort();
    registry.add(
        "spring.security.oauth2.client.provider.github.authorization-uri",
        () -> base + "/login/oauth/authorize");
    registry.add(
        "spring.security.oauth2.client.provider.github.token-uri",
        () -> base + "/login/oauth/access_token");
    registry.add(
        "spring.security.oauth2.client.provider.github.user-info-uri",
        () -> base + "/login/oauth/user");
    registry.add(
        "spring.security.oauth2.client.provider.github.user-name-attribute", () -> "login");
    registry.add(
        "security.oauth2.external-auth-token-filter.jwt.jwk-set-uri", () -> base + "/keys");
  }

  @BeforeEach
  void stubProvider() {
    provider.stubFor(
        WireMock.get(urlPathEqualTo("/keys"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(signingKey.toPublicJWK()).toString())));
    provider.stubFor(
        WireMock.get(urlPathEqualTo("/login/oauth/user"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"login\": \"user-info-user\", \"email\": \"u@example.com\"}")));
  }

  @Test
  void jwtForThisGateAuthenticates() throws Exception {
    ResponseEntity<String> response = get(token(AUDIENCE, "access"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("authenticated");
    provider.verify(0, getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }

  @Test
  void jwtForAnotherAudienceIsRejected() throws Exception {
    ResponseEntity<String> response = get(token("https://graph.microsoft.com", "access"));

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    provider.verify(0, getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }

  @Test
  void jwtWithoutTheRequiredScopeIsRejected() throws Exception {
    ResponseEntity<String> response = get(token(AUDIENCE, "openid profile"));

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
  }

  @Test
  void tokenOnlyTheUserInfoEndpointAcceptsIsRejectedWithoutFallback() {
    ResponseEntity<String> response = get("opaque-personal-access-token");

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    provider.verify(0, getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }

  private ResponseEntity<String> get(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(bearerToken);
    return noRedirects.exchange(
        "http://localhost:" + appPort + "/testOAuth2Api",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);
  }

  private static String token(String audience, String scp) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(audience)
            .subject("subject-1")
            .claim("preferred_username", "jwt-user@example.com")
            .claim("scp", scp)
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  private static RestTemplate noRedirectRestTemplate() {
    RestTemplate restTemplate =
        new RestTemplate(
            new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableRedirectHandling().build()));
    restTemplate.setErrorHandler(
        new DefaultResponseErrorHandler() {
          @Override
          public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
            return false;
          }
        });
    return restTemplate;
  }
}
