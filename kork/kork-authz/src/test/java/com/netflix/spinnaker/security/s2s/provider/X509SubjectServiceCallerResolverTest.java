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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class X509SubjectServiceCallerResolverTest {

  private final X509SubjectServiceCallerResolver resolver =
      new X509SubjectServiceCallerResolver(
          Pattern.compile("CN=([^,]+)"), new SpinnakerServiceMapper("spin-"));

  @Test
  void extractsServiceFromCommonName() {
    MockHttpServletRequest request = requestWithPeerSubject("CN=orca,OU=spinnaker,O=example");

    Optional<ServiceCaller> caller = resolver.resolve(request);

    assertThat(caller).isPresent();
    assertThat(caller.get().service()).isEqualTo(SpinnakerService.ORCA);
    assertThat(caller.get().source()).isEqualTo("x509-subject");
  }

  @Test
  void stripsDeploymentPrefix() {
    MockHttpServletRequest request = requestWithPeerSubject("CN=spin-echo,O=example");

    assertThat(resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ECHO);
  }

  @Test
  void emptyWhenNoPeerCertificate() {
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }

  @Test
  void emptyWhenPeerCertificateArrayIsEmpty() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[0]);

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void emptyWhenAttributeIsNotACertificateArray() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("jakarta.servlet.request.X509Certificate", "not-a-cert");

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void emptyWhenSubjectDnDoesNotMatchRegex() {
    // A verified cert is present, but nothing in the DN matches the CN regex: we must not
    // fabricate an identity, so the caller falls through to the next resolver / anonymous path.
    MockHttpServletRequest request = requestWithPeerSubject("OU=spinnaker,O=example");

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void mapsToUnknownWhenCommonNameIsNotASpinnakerService() {
    // A trusted-CA cert whose CN is not a known service still resolves (the peer is authenticated),
    // but maps to UNKNOWN so authorization can deny it rather than silently granting service
    // rights.
    MockHttpServletRequest request = requestWithPeerSubject("CN=some-random-client,O=example");

    assertThat(resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.UNKNOWN);
  }

  @Test
  void usesTheLeafCertificateWhenAChainIsPresent() {
    // The servlet contract puts the peer's own (leaf) certificate first; intermediates/CA certs
    // that follow must not be used to derive the identity.
    X509Certificate leaf = certWithSubject("CN=orca,O=example");
    X509Certificate intermediate = certWithSubject("CN=intermediate-ca,O=example");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        "jakarta.servlet.request.X509Certificate", new X509Certificate[] {leaf, intermediate});

    assertThat(resolver.resolve(request))
        .map(ServiceCaller::service)
        .contains(SpinnakerService.ORCA);
  }

  private static X509Certificate certWithSubject(String subjectDn) {
    X509Certificate certificate = mock(X509Certificate.class);
    when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal(subjectDn));
    return certificate;
  }

  private static MockHttpServletRequest requestWithPeerSubject(String subjectDn) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        "jakarta.servlet.request.X509Certificate",
        new X509Certificate[] {certWithSubject(subjectDn)});
    return request;
  }
}
