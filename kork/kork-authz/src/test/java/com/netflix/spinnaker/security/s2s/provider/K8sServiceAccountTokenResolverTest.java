/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class K8sServiceAccountTokenResolverTest {

  private static final String TOKEN_HEADER = "X-Service-Identity-Token";

  private RSAKey signingKey;
  private K8sServiceAccountTokenResolver resolver;

  @BeforeEach
  void setUp() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(
            JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()))));
    processor.setJWTClaimsSetVerifier(
        new DefaultJWTClaimsVerifier<>(new JWTClaimsSet.Builder().build(), Set.of("sub", "exp")));
    resolver =
        new K8sServiceAccountTokenResolver(
            processor, TOKEN_HEADER, "spinnaker", new SpinnakerServiceMapper("spin-"));
  }

  @Test
  void resolvesServiceAccountSubjectToService() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, sign("system:serviceaccount:spinnaker:spin-orca"));

    Optional<ServiceCaller> caller = resolver.resolve(request);

    assertThat(caller).isPresent();
    assertThat(caller.get().service()).isEqualTo(SpinnakerService.ORCA);
    assertThat(caller.get().source()).isEqualTo("k8s-sa-token");
  }

  @Test
  void toleratesBearerPrefix() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, "Bearer " + sign("system:serviceaccount:spinnaker:spin-echo"));

    assertThat(resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ECHO);
  }

  @Test
  void wrongNamespaceIsUnknown() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, sign("system:serviceaccount:other-ns:spin-orca"));

    assertThat(resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.UNKNOWN);
  }

  @Test
  void nonServiceAccountSubjectIsEmpty() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, sign("some-user@example.com"));

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void invalidTokenIsEmpty() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, "not-a-jwt");

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void unsignedByTrustedKeyIsEmpty() throws Exception {
    RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("system:serviceaccount:spinnaker:spin-orca")
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(attackerKey.getKeyID()).build(),
            claims);
    jwt.sign(new RSASSASigner(attackerKey));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, jwt.serialize());

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void defaultAlgorithmsVerifyRs256Token() throws Exception {
    K8sServiceAccountTokenResolver rs256Resolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())),
            null,
            List.of(),
            List.of("RS256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, sign("system:serviceaccount:spinnaker:spin-orca"));

    assertThat(rs256Resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ORCA);
  }

  @Test
  void es256ConfiguredResolverVerifiesEs256Token() throws Exception {
    ECKey ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-kid").generate();
    K8sServiceAccountTokenResolver es256Resolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(ecKey.toPublicJWK())),
            null,
            List.of(),
            List.of("ES256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, signEs256(ecKey, "system:serviceaccount:spinnaker:spin-echo"));

    assertThat(es256Resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ECHO);
  }

  @Test
  void tokenSignedWithUnlistedAlgorithmIsRejected() throws Exception {
    ECKey ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-kid").generate();
    // JWKS contains the EC public key, but the resolver only accepts RS256 — so the ES256 token is
    // rejected on algorithm, not on a missing key.
    K8sServiceAccountTokenResolver rs256Resolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(
                new JWKSet(List.of(signingKey.toPublicJWK(), ecKey.toPublicJWK()))),
            null,
            List.of(),
            List.of("RS256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, signEs256(ecKey, "system:serviceaccount:spinnaker:spin-orca"));

    assertThat(rs256Resolver.resolve(request)).isEmpty();
  }

  @Test
  void emptyAlgorithmListFallsBackToRs256() throws Exception {
    K8sServiceAccountTokenResolver resolverWithNoAlgorithms =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())),
            null,
            List.of(),
            List.of(),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, sign("system:serviceaccount:spinnaker:spin-orca"));

    assertThat(resolverWithNoAlgorithms.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ORCA);
  }

  @Test
  void expiredTokenIsEmpty() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        TOKEN_HEADER,
        signClaims(
            new JWTClaimsSet.Builder()
                .subject("system:serviceaccount:spinnaker:spin-orca")
                .expirationTime(new Date(System.currentTimeMillis() - 60_000))
                .build()));

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void matchingAudienceResolves() throws Exception {
    K8sServiceAccountTokenResolver audienceResolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())),
            null,
            List.of("spinnaker-audience"),
            List.of("RS256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        TOKEN_HEADER,
        signClaims(
            new JWTClaimsSet.Builder()
                .subject("system:serviceaccount:spinnaker:spin-orca")
                .audience("spinnaker-audience")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build()));

    assertThat(audienceResolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ORCA);
  }

  @Test
  void wrongAudienceIsEmpty() throws Exception {
    K8sServiceAccountTokenResolver audienceResolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())),
            null,
            List.of("spinnaker-audience"),
            List.of("RS256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        TOKEN_HEADER,
        signClaims(
            new JWTClaimsSet.Builder()
                .subject("system:serviceaccount:spinnaker:spin-orca")
                .audience("some-other-audience")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build()));

    assertThat(audienceResolver.resolve(request)).isEmpty();
  }

  @Test
  void matchingIssuerResolves() throws Exception {
    K8sServiceAccountTokenResolver issuerResolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())),
            "https://kubernetes.default.svc",
            List.of(),
            List.of("RS256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        TOKEN_HEADER,
        signClaims(
            new JWTClaimsSet.Builder()
                .subject("system:serviceaccount:spinnaker:spin-orca")
                .issuer("https://kubernetes.default.svc")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build()));

    assertThat(issuerResolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ORCA);
  }

  @Test
  void wrongIssuerIsEmpty() throws Exception {
    K8sServiceAccountTokenResolver issuerResolver =
        K8sServiceAccountTokenResolver.build(
            new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())),
            "https://kubernetes.default.svc",
            List.of(),
            List.of("RS256"),
            TOKEN_HEADER,
            "spinnaker",
            new SpinnakerServiceMapper("spin-"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        TOKEN_HEADER,
        signClaims(
            new JWTClaimsSet.Builder()
                .subject("system:serviceaccount:spinnaker:spin-orca")
                .issuer("https://attacker.example.com")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build()));

    assertThat(issuerResolver.resolve(request)).isEmpty();
  }

  private String signEs256(ECKey key, String subject) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new ECDSASigner(key));
    return jwt.serialize();
  }

  private String sign(String subject) throws Exception {
    return signClaims(
        new JWTClaimsSet.Builder()
            .subject(subject)
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build());
  }

  private String signClaims(JWTClaimsSet claims) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }
}
