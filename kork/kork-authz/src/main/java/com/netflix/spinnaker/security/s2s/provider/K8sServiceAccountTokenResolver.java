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

import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.ServiceCallerResolver;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the service caller from a Kubernetes projected ServiceAccount token — the
 * no-certificates path for plain Kubernetes installs.
 *
 * <p>Every Spinnaker pod already runs under a per-service ServiceAccount; its audience-bound
 * projected token is presented on a header and verified offline against the cluster JWKS. The token
 * subject is {@code system:serviceaccount:<namespace>:<name>}; the identity is the ServiceAccount,
 * not the pod, so every replica of a service resolves to the same {@link SpinnakerService}. The
 * namespace is matched so a same-named ServiceAccount in another namespace cannot impersonate a
 * Spinnaker service.
 *
 * <p><b>Accepted residual risk:</b> because verification is entirely offline there is no revocation
 * path — a leaked token remains valid for its full TTL, and a token bound to a Pod stays valid
 * after that Pod is gone. The compensating control is a short projected-token TTL (the README
 * recommends {@code expirationSeconds: 3600}); Kubernetes rotates projected tokens automatically,
 * so keeping the TTL short is essentially free.
 *
 * <p>Kubernetes offers a stronger alternative in the {@code TokenReview} API, which validates bound
 * claims against live cluster state. It is deliberately not used here: {@code tokenreviews} is a
 * non-namespaced resource, so it can only be granted through a <em>ClusterRoleBinding</em> to
 * {@code system:auth-delegator}. Requiring cluster-scoped RBAC to install a namespace-scoped
 * application is too heavy a precondition for an open-source deployment, whereas offline
 * verification needs no RBAC at all — the cluster already authorizes every ServiceAccount to read
 * the JWKS. If your threat model needs bound-claim validation, prefer {@code x509-subject} or a
 * service mesh over widening RBAC.
 */
public class K8sServiceAccountTokenResolver implements ServiceCallerResolver {

  static final String SOURCE = "k8s-sa-token";
  private static final String SUBJECT_PREFIX = "system:serviceaccount:";
  private static final Logger log = LoggerFactory.getLogger(K8sServiceAccountTokenResolver.class);

  private final JWTProcessor<SecurityContext> jwtProcessor;
  private final String tokenHeader;
  private final String namespace;
  private final SpinnakerServiceMapper mapper;

  public K8sServiceAccountTokenResolver(
      JWTProcessor<SecurityContext> jwtProcessor,
      String tokenHeader,
      String namespace,
      SpinnakerServiceMapper mapper) {
    this.jwtProcessor = jwtProcessor;
    this.tokenHeader = tokenHeader;
    this.namespace = namespace;
    this.mapper = mapper;
  }

  /**
   * Builds a resolver that verifies projected-token signatures against the cluster JWKS at {@code
   * jwksUri} using the accepted {@code jwsAlgorithms} (RS256 by default; some clusters use ES256),
   * enforcing the optional issuer and audience.
   *
   * <p>The JWKS is fetched through {@link ClusterJwksRetriever}, which supplies the cluster CA and
   * this pod's own credential — both required against an in-cluster API server, and neither needing
   * any RBAC to be granted. Explicit connect/read timeouts and a response size cap keep a slow,
   * unreachable, or hostile JWKS endpoint from stalling {@link #resolve} calls (which run
   * synchronously on request threads) or exhausting memory. {@link RemoteJWKSet} caches the keys,
   * so the fetch only happens on the first resolve and on subsequent refreshes.
   */
  public static K8sServiceAccountTokenResolver build(
      String jwksUri,
      String issuer,
      List<String> audiences,
      List<String> jwsAlgorithms,
      String tokenHeader,
      String namespace,
      SpinnakerServiceMapper mapper,
      String jwksCaCertPath,
      String jwksTokenPath,
      Duration jwksTokenRefresh,
      int jwksConnectTimeoutMs,
      int jwksReadTimeoutMs,
      int jwksSizeLimitBytes)
      throws GeneralSecurityException, IOException {
    ClusterJwksRetriever retriever =
        ClusterJwksRetriever.build(
            jwksCaCertPath,
            jwksTokenPath,
            jwksTokenRefresh,
            jwksConnectTimeoutMs,
            jwksReadTimeoutMs,
            jwksSizeLimitBytes);
    return build(
        new RemoteJWKSet<>(new URL(jwksUri), retriever),
        issuer,
        audiences,
        jwsAlgorithms,
        tokenHeader,
        namespace,
        mapper);
  }

  /**
   * Builds a resolver against an already-constructed {@link JWKSource}. Package-visible so tests
   * can inject an in-memory JWKS and exercise the real algorithm selection without network access.
   */
  static K8sServiceAccountTokenResolver build(
      JWKSource<SecurityContext> keySource,
      String issuer,
      List<String> audiences,
      List<String> jwsAlgorithms,
      String tokenHeader,
      String namespace,
      SpinnakerServiceMapper mapper) {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(parseAlgorithms(jwsAlgorithms), keySource));

    JWTClaimsSet.Builder exactMatch = new JWTClaimsSet.Builder();
    if (issuer != null && !issuer.isBlank()) {
      exactMatch.issuer(issuer);
    }
    Set<String> required = Set.of("sub", "exp");
    DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier =
        (audiences == null || audiences.isEmpty())
            ? new DefaultJWTClaimsVerifier<>(exactMatch.build(), required)
            : new DefaultJWTClaimsVerifier<>(
                Set.copyOf(audiences), exactMatch.build(), required, Set.of());
    processor.setJWTClaimsSetVerifier(claimsVerifier);
    return new K8sServiceAccountTokenResolver(processor, tokenHeader, namespace, mapper);
  }

  /**
   * Parses the configured accepted signature algorithms, ignoring blanks and falling back to {@code
   * RS256} when none are usable.
   */
  private static Set<JWSAlgorithm> parseAlgorithms(List<String> jwsAlgorithms) {
    Set<JWSAlgorithm> parsed = new LinkedHashSet<>();
    if (jwsAlgorithms != null) {
      for (String algorithm : jwsAlgorithms) {
        if (algorithm != null && !algorithm.isBlank()) {
          parsed.add(JWSAlgorithm.parse(algorithm.trim()));
        }
      }
    }
    if (parsed.isEmpty()) {
      parsed.add(JWSAlgorithm.RS256);
    }
    return parsed;
  }

  @Override
  public Optional<ServiceCaller> resolve(HttpServletRequest request) {
    String token = request.getHeader(tokenHeader);
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
      token = token.substring(7).trim();
    }
    JWTClaimsSet claims;
    try {
      claims = jwtProcessor.process(token, null);
    } catch (RemoteKeySourceException e) {
      // The JWKS could not be retrieved, so no caller can be authenticated regardless of the token
      // they present. This is an operator misconfiguration, not untrusted input, so it is logged
      // loudly: a silent debug line here is what makes this failure mode near-undiagnosable, since
      // the only other symptom is a generic "no authenticated caller" denial.
      log.error(
          "Cannot verify service-account tokens: failed to retrieve JWKS. Check that "
              + "authz.s2s.k8s.jwks-uri is reachable, that authz.s2s.k8s.jwks-ca-cert-path is the "
              + "CA signing its certificate, and that authz.s2s.k8s.jwks-token-path is readable. "
              + "Cause: {}",
          e.getMessage());
      return Optional.empty();
    } catch (BadJOSEException | ParseException e) {
      // Untrusted input: a malformed, expired, or wrongly-audienced token. Expected in normal
      // operation (e.g. end-user requests carrying no service identity), so kept quiet.
      log.debug("Rejected service-account token: {}", e.getMessage());
      return Optional.empty();
    } catch (JOSEException e) {
      log.warn("Failed to verify service-account token: {}", e.getMessage());
      return Optional.empty();
    }

    String subject = claims.getSubject();
    if (subject == null || !subject.startsWith(SUBJECT_PREFIX)) {
      return Optional.empty();
    }
    // system:serviceaccount:<namespace>:<name>
    String[] parts = subject.substring(SUBJECT_PREFIX.length()).split(":", 2);
    if (parts.length != 2) {
      return Optional.empty();
    }
    String tokenNamespace = parts[0];
    String serviceAccountName = parts[1];
    SpinnakerService service =
        namespace.equals(tokenNamespace)
            ? mapper.map(serviceAccountName)
            : SpinnakerService.UNKNOWN;
    return Optional.of(new ServiceCaller(service, subject, SOURCE));
  }
}
