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

package com.netflix.spinnaker.gate.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class ExternalJwtDecoderFactoryTest {

  private static final String ISSUER = "https://login.example.com/tenant/v2.0";
  private static final String AUDIENCE = "gate-api";

  private static RSAKey signingKey;
  private static HttpServer jwksServer;
  private static String jwkSetUri;

  @BeforeAll
  static void startJwksServer() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    jwksServer.createContext(
        "/keys",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, jwks.length);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(jwks);
          }
        });
    jwksServer.start();
    jwkSetUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/keys";
  }

  @AfterAll
  static void stopJwksServer() {
    jwksServer.stop(0);
  }

  @Test
  void decodesATokenSignedByTheIssuerForTheConfiguredAudience() throws Exception {
    JwtDecoder decoder = ExternalJwtDecoderFactory.create(properties());

    Jwt jwt = decoder.decode(sign(signingKey, claims -> {}));

    assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("user@example.com");
    assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
  }

  @Test
  void rejectsATokenSignedWithAnotherKey() throws Exception {
    JwtDecoder decoder = ExternalJwtDecoderFactory.create(properties());
    RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();

    String token = sign(otherKey, claims -> {});

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsATokenForAnotherAudience() throws Exception {
    JwtDecoder decoder = ExternalJwtDecoderFactory.create(properties());

    String token = sign(signingKey, claims -> claims.audience("https://graph.microsoft.com"));

    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("aud");
  }

  @Test
  void rejectsATokenFromAnotherIssuer() throws Exception {
    JwtDecoder decoder = ExternalJwtDecoderFactory.create(properties());

    String token = sign(signingKey, claims -> claims.issuer("https://evil.example.com"));

    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("iss");
  }

  @Test
  void rejectsAnExpiredToken() throws Exception {
    JwtDecoder decoder = ExternalJwtDecoderFactory.create(properties());

    String token =
        sign(
            signingKey,
            claims ->
                claims
                    .issueTime(Date.from(Instant.now().minusSeconds(7200)))
                    .expirationTime(Date.from(Instant.now().minusSeconds(3600))));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsAnOpaqueToken() {
    JwtDecoder decoder = ExternalJwtDecoderFactory.create(properties());

    assertThatThrownBy(() -> decoder.decode("ghp_not-a-jwt")).isInstanceOf(JwtException.class);
  }

  @Test
  void doesNotFetchKeysBeforeTheFirstToken() {
    ExternalAuthTokenFilterConfigurationProperties.Jwt unreachable = properties();
    unreachable.setJwkSetUri("http://127.0.0.1:1/keys");

    JwtDecoder decoder = ExternalJwtDecoderFactory.create(unreachable);

    assertThat(decoder).isNotNull();
  }

  @Test
  void requiresAnAudience() {
    ExternalAuthTokenFilterConfigurationProperties.Jwt noAudience = properties();
    noAudience.setAudiences(List.of());

    assertThatThrownBy(() -> ExternalJwtDecoderFactory.create(noAudience))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("jwt.audiences");
  }

  @Test
  void acceptsAnyConfiguredAudience() {
    ExternalAuthTokenFilterConfigurationProperties.Jwt twoAudiences = properties();
    twoAudiences.setAudiences(List.of("api://gate", AUDIENCE));

    OAuth2TokenValidator<Jwt> validator = ExternalJwtDecoderFactory.validator(twoAudiences);

    assertThat(validator.validate(jwt(List.of(AUDIENCE), null)).hasErrors()).isFalse();
    assertThat(validator.validate(jwt(List.of("other"), null)).hasErrors()).isTrue();
  }

  @Test
  void requiresTheConfiguredScopesFromAScpString() {
    ExternalAuthTokenFilterConfigurationProperties.Jwt withScopes = properties();
    withScopes.setRequiredScopes(List.of("access"));

    OAuth2TokenValidator<Jwt> validator = ExternalJwtDecoderFactory.validator(withScopes);

    assertThat(validator.validate(jwt(List.of(AUDIENCE), "access other")).hasErrors()).isFalse();
    assertThat(validator.validate(jwt(List.of(AUDIENCE), "other")).hasErrors()).isTrue();
    assertThat(validator.validate(jwt(List.of(AUDIENCE), null)).hasErrors()).isTrue();
  }

  @Test
  void readsScopesFromAScopeList() {
    ExternalAuthTokenFilterConfigurationProperties.Jwt withScopes = properties();
    withScopes.setRequiredScopes(List.of("access"));

    OAuth2TokenValidator<Jwt> validator = ExternalJwtDecoderFactory.validator(withScopes);
    Jwt token = baseJwt(List.of(AUDIENCE)).claim("scope", List.of("openid", "access")).build();

    assertThat(validator.validate(token).hasErrors()).isFalse();
  }

  private static ExternalAuthTokenFilterConfigurationProperties.Jwt properties() {
    ExternalAuthTokenFilterConfigurationProperties.Jwt properties =
        new ExternalAuthTokenFilterConfigurationProperties.Jwt();
    properties.setIssuerUri(ISSUER);
    properties.setJwkSetUri(jwkSetUri);
    properties.setAudiences(List.of(AUDIENCE));
    return properties;
  }

  private static String sign(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer)
      throws JOSEException {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject("subject-1")
            .claim("preferred_username", "user@example.com")
            .claim("scp", "access")
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
    customizer.accept(claims);
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static Jwt jwt(List<String> audience, String scp) {
    Jwt.Builder builder = baseJwt(audience);
    if (scp != null) {
      builder.claim("scp", scp);
    }
    return builder.build();
  }

  private static Jwt.Builder baseJwt(List<String> audience) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuer(ISSUER)
        .audience(audience)
        .subject("subject-1")
        .issuedAt(Instant.now().minusSeconds(60))
        .expiresAt(Instant.now().plusSeconds(3600));
  }
}
