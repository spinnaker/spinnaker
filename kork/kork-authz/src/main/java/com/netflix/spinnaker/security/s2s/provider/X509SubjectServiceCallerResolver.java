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
import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the service caller from the peer's verified mTLS client certificate (the CA/mTLS path).
 *
 * <p>The certificate is trusted at the transport layer by the container (client-auth required,
 * validated against the configured truststore/CA); this resolver only reads the already-verified
 * peer certificate that the servlet container exposes and extracts the service name from its
 * subject DN via a configured regex (the Common Name by default).
 *
 * <h2>Trust assumptions</h2>
 *
 * <p>This resolver performs <em>no</em> cryptographic verification of its own. It relies entirely
 * on three things being configured correctly outside this class, and its output is only as
 * trustworthy as the weakest of them:
 *
 * <ol>
 *   <li><b>Container client-auth.</b> The {@code jakarta.servlet.request.X509Certificate} attribute
 *       is populated only for a cert the container accepted during the TLS handshake. With the
 *       default JSSE {@code X509TrustManager}, {@code want} mode relaxes the <em>presence</em>
 *       requirement (a request with no client cert still reaches the app, and this resolver returns
 *       {@link Optional#empty()}) but <em>not</em> the validation requirement: a presented cert
 *       that does not chain to the truststore aborts the handshake and never becomes this
 *       attribute. A self-signed / untrusted cert is therefore not reachable here <em>unless</em> a
 *       permissive or custom trust manager is wired in — that would be a container
 *       misconfiguration, not something this resolver can defend against.
 *   <li><b>Truststore scope.</b> Because the container accepts <em>any</em> cert that chains to the
 *       configured truststore, the {@code subjectRegex} + {@link SpinnakerServiceMapper} are the
 *       only thing narrowing "a valid cert" down to "this specific Spinnaker service." If the
 *       truststore trusts a broad CA that can mint certs for arbitrary subjects, that CA is part of
 *       this service's trust boundary.
 *   <li><b>Subject DN format.</b> {@link javax.security.auth.x500.X500Principal#getName()} returns
 *       the DN in RFC 2253 form (e.g. {@code CN=orca,OU=spinnaker,O=example}), not the OpenSSL
 *       {@code /CN=orca} form. The configured regex must be authored against RFC 2253.
 * </ol>
 *
 * <p>Recognition, not authorization: mapping a DN to a {@link SpinnakerService} only decides
 * <em>who</em> a peer is. What that service is permitted to do is enforced separately.
 */
public class X509SubjectServiceCallerResolver implements ServiceCallerResolver {

  static final String PEER_CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
  static final String SOURCE = "x509-subject";
  private static final Logger log = LoggerFactory.getLogger(X509SubjectServiceCallerResolver.class);

  private final Pattern subjectRegex;
  private final SpinnakerServiceMapper mapper;

  public X509SubjectServiceCallerResolver(Pattern subjectRegex, SpinnakerServiceMapper mapper) {
    this.subjectRegex = subjectRegex;
    this.mapper = mapper;
  }

  @Override
  public Optional<ServiceCaller> resolve(HttpServletRequest request) {
    Object attribute = request.getAttribute(PEER_CERTIFICATE_ATTRIBUTE);
    if (!(attribute instanceof X509Certificate[] certs) || certs.length == 0) {
      return Optional.empty();
    }
    String subjectDn = certs[0].getSubjectX500Principal().getName();
    Matcher matcher = subjectRegex.matcher(subjectDn);
    if (!matcher.find() || matcher.groupCount() < 1 || matcher.group(1) == null) {
      // A cert the container already validated is present, but its subject DN does not match the
      // configured regex, so we cannot attribute it to a service. This is distinct from "no cert"
      // and usually signals a regex/truststore misconfiguration worth surfacing.
      log.debug(
          "Verified peer certificate present but subject DN did not match regex: {}", subjectDn);
      return Optional.empty();
    }
    SpinnakerService service = mapper.map(matcher.group(1));
    return Optional.of(new ServiceCaller(service, subjectDn, SOURCE));
  }
}
