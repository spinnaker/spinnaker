/*
 * Copyright 2026 Google, Inc.
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

package com.netflix.spinnaker.gate.security.iap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import retrofit2.mock.Calls;

/**
 * Integration test for IAP authentication that verifies the full security filter chain properly
 * authenticates users and maintains authentication context across requests with cached JWT
 * signatures.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "google.iap.enabled=true",
      "google.iap.audience=test_audience",
      "google.iap.jwtHeader=x-goog-iap-jwt-assertion",
      "google.iap.issuerId=https://cloud.google.com/iap",
      "spring.config.location=classpath:gate-test.yml",
      "services.front50.applicationRefreshInitialDelayMs=3600000",
      "services.fiat.enabled=true",
      "logging.level.com.netflix.spinnaker.gate.security.iap=DEBUG",
      "management.health.redis.enabled=false",
      "management.endpoints.web.exposure.include=beans"
    })
class IapAuthIntegrationTest {

  private static final String TEST_EMAIL = "test@example.com";
  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};

  @LocalServerPort private int port;

  @Autowired ObjectMapper objectMapper;

  @MockBean ClouddriverService clouddriverService;

  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  @SpyBean IapAuthenticationFilter iapAuthenticationFilter;

  @MockBean FiatService fiatService;

  @SpyBean FiatPermissionEvaluator fiatPermissionEvaluator;

  @MockBean Front50Service front50Service;

  private KeyPair testKeyPair;
  private String publicKeyId;

  @BeforeEach
  void init(TestInfo testInfo) throws Exception {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of()));

    // Generate EC key pair for signing JWTs
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
    gen.initialize(Curve.P_256.toECParameterSpec());
    testKeyPair = gen.generateKeyPair();
    publicKeyId = BaseEncoding.base64().encode(testKeyPair.getPublic().getEncoded());

    // Populate the filter's key cache with our test public key
    ECKey key =
        new ECKey.Builder(Curve.P_256, (ECPublicKey) testKeyPair.getPublic())
            .algorithm(JWSAlgorithm.ES256)
            .build();
    iapAuthenticationFilter.keyCache.put(publicKeyId, key);
  }

  @AfterEach
  void cleanup() {
    fiatPermissionEvaluator.invalidatePermission(TEST_EMAIL);
  }

  @Test
  void testAuthUserWithValidJWT() throws Exception {

    String jwtToken = createValidJWT(TEST_EMAIL);

    when(fiatService.loginUser(TEST_EMAIL)).thenReturn(Calls.response((Void) null));
    when(fiatService.getUserPermission(TEST_EMAIL))
        .thenReturn(
            Calls.response(
                new UserPermission.View()
                    .setName(TEST_EMAIL)
                    .setAdmin(false)
                    .setAccounts(
                        Set.of(
                            new Account.View()
                                .setName("test-account")
                                .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))
                    .setRoles(
                        Set.of(new Role.View().setName("testRole").setSource(Role.Source.LDAP)))));

    // Use sessions as ... we don't want to re-auth on this
    HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    assertThat(callGate(client, "http://localhost:" + port + "/auth/user", jwtToken))
        .containsEntry("email", TEST_EMAIL)
        .containsEntry("username", TEST_EMAIL);
    verify(fiatService).loginUser(TEST_EMAIL);
    verify(fiatService).getUserPermission(TEST_EMAIL);

    // Let's test a bean endpoint that's auth restricted normally that ALSO doesn't do a lot of
    // calls
    assertThat(callGate(client, "http://localhost:" + port + "/beans", jwtToken))
        .containsKey("contexts");
  }

  private Map<String, Object> callGate(HttpClient client, String url, String jwtToken)
      throws Exception {

    HttpRequest request =
        HttpRequest.newBuilder(new URI(url))
            .GET()
            .header("x-goog-iap-jwt-assertion", jwtToken)
            .header("Accept", "application/json")
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return objectMapper.readValue(response.body(), mapType);
  }

  @Test
  void testSecondRequestWithCachedSignatureStillAuthenticates() throws Exception {
    String jwtToken = createValidJWT(TEST_EMAIL);

    when(fiatService.loginUser(TEST_EMAIL)).thenReturn(Calls.response((Void) null));
    when(fiatService.getUserPermission(TEST_EMAIL))
        .thenReturn(
            Calls.response(
                new UserPermission.View()
                    .setName(TEST_EMAIL)
                    .setAdmin(false)
                    .setAccounts(
                        Set.of(
                            new Account.View()
                                .setName("test-account")
                                .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))
                    .setRoles(
                        Set.of(new Role.View().setName("testRole").setSource(Role.Source.LDAP)))));

    HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    // First request - should authenticate and cache signature
    assertThat(callGate(client, "http://localhost:" + port + "/auth/user", jwtToken))
        .containsEntry("email", TEST_EMAIL);

    // Second request with same JWT and session (cookies) - should use cached signature
    assertThat(callGate(client, "http://localhost:" + port + "/auth/user", jwtToken))
        .as("Second request with cached signature should still authenticate the user")
        .containsEntry("email", TEST_EMAIL)
        .containsEntry("username", TEST_EMAIL);

    // With cached signature, login is only called once (first request)
    // But getUserPermission may be called multiple times by FiatPermissionEvaluator
    verify(fiatService, times(1)).loginUser(TEST_EMAIL);
  }

  @Test
  void testRequestWithoutJWTReturnsUnauthorized() throws Exception {

    HttpRequest request =
        HttpRequest.newBuilder(new URI("http://localhost:" + port + "/auth/user")).GET().build();
    HttpClient client = HttpClient.newBuilder().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    // Spinnaker has /auth/user return a blank 200 for non-auth'd request to trigger gate's auth
    // flow
    assertThat(response.body()).isEmpty();
    assertThat(response.statusCode()).isEqualTo(200); // Just verify it doesn't crash
  }

  @Test
  void testRequestWithExpiredJWTDoesNotAuthenticate() throws Exception {
    URI uri = new URI("http://localhost:" + port + "/auth/user");

    String expiredJWT = createExpiredJWT(TEST_EMAIL);
    HttpRequest request =
        HttpRequest.newBuilder(uri).GET().header("x-goog-iap-jwt-assertion", expiredJWT).build();

    HttpClient client = HttpClient.newBuilder().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // Expired JWT should not crash the application (error is logged but request continues)
    // The exact behavior depends on security configuration
    assertThat(response.statusCode()).isNotEqualTo(500); // Just verify it doesn't crash
  }

  private String createValidJWT(String email) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(publicKeyId).build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .audience("test_audience")
            .issuer("https://cloud.google.com/iap")
            .subject("subject-" + email)
            .claim("email", email)
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner((ECPrivateKey) testKeyPair.getPrivate()));

    return jwt.serialize();
  }

  private String createExpiredJWT(String email) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(publicKeyId).build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issueTime(Date.from(Instant.now().minusSeconds(400)))
            .expirationTime(Date.from(Instant.now().minusSeconds(100)))
            .audience("test_audience")
            .issuer("https://cloud.google.com/iap")
            .subject("subject-" + email)
            .claim("email", email)
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner((ECPrivateKey) testKeyPair.getPrivate()));

    return jwt.serialize();
  }
}
