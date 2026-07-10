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

package com.netflix.spinnaker.gate.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class AuthTypeResolverTest {

  @BeforeEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // resolveAuthType — explicit request attribute wins
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("AUTH_TYPE_ATTRIBUTE wins over SecurityContext heuristics")
  void explicitAttributeWins() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, "custom_type");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(AuthTypeResolver.resolveAuthType(request)).isEqualTo("custom_type");
  }

  // ---------------------------------------------------------------------------
  // resolveAuthType — SecurityContext heuristics
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("no authentication → none")
  void noAuthIsNone() {
    assertThat(AuthTypeResolver.resolveAuthType(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.TYPE_NONE);
  }

  @Test
  @DisplayName("AnonymousAuthenticationToken → anonymous")
  void anonymousAuth() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThat(AuthTypeResolver.resolveAuthType(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.TYPE_ANONYMOUS);
  }

  @Test
  @DisplayName("UsernamePasswordAuthenticationToken (authenticated) → session")
  void sessionAuth() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(AuthTypeResolver.resolveAuthType(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.TYPE_SESSION);
  }

  @Test
  @DisplayName("PreAuthenticatedAuthenticationToken → pre_authenticated (without explicit tag)")
  void preAuthenticatedFallback() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new PreAuthenticatedAuthenticationToken(
                "iap-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(AuthTypeResolver.resolveAuthType(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.TYPE_PRE_AUTHENTICATED);
  }

  @Test
  @DisplayName("auth class name containing 'OAuth2' → oauth2")
  void oauth2ByClassName() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new OAuth2LikeAuthenticationToken(
                "bob", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(AuthTypeResolver.resolveAuthType(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.TYPE_OAUTH2);
  }

  @Test
  @DisplayName("auth class name containing 'X509' → x509")
  void x509ByClassName() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new X509ClientCertificateAuthenticationToken(
                "cn=alice", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(AuthTypeResolver.resolveAuthType(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.TYPE_X509);
  }

  // ---------------------------------------------------------------------------
  // resolvePrincipalKind
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("PRINCIPAL_KIND_ATTRIBUTE is lowercased on the way out")
  void principalKindLowercased() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE, "SERVICE_ACCOUNT");

    assertThat(AuthTypeResolver.resolvePrincipalKind(request)).isEqualTo("service_account");
  }

  @Test
  @DisplayName("missing PRINCIPAL_KIND_ATTRIBUTE → unknown")
  void principalKindMissing() {
    assertThat(AuthTypeResolver.resolvePrincipalKind(new MockHttpServletRequest()))
        .isEqualTo(AuthTypeResolver.PRINCIPAL_KIND_UNKNOWN);
  }

  @Test
  @DisplayName("empty PRINCIPAL_KIND_ATTRIBUTE → unknown")
  void principalKindEmptyString() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE, "");

    assertThat(AuthTypeResolver.resolvePrincipalKind(request))
        .isEqualTo(AuthTypeResolver.PRINCIPAL_KIND_UNKNOWN);
  }

  // ---------------------------------------------------------------------------
  // Test-only Authentication subclasses to exercise class-name-based heuristics
  // ---------------------------------------------------------------------------

  /** Subclass whose simple name contains "OAuth2" — exercises the OAuth2 class-name branch. */
  private static final class OAuth2LikeAuthenticationToken
      extends UsernamePasswordAuthenticationToken {
    OAuth2LikeAuthenticationToken(
        Object principal, java.util.Collection<SimpleGrantedAuthority> authorities) {
      super(principal, null, authorities);
    }
  }

  /** Subclass whose simple name contains "X509" — exercises the X509 class-name branch. */
  private static final class X509ClientCertificateAuthenticationToken
      extends UsernamePasswordAuthenticationToken {
    X509ClientCertificateAuthenticationToken(
        Object principal, java.util.Collection<SimpleGrantedAuthority> authorities) {
      super(principal, null, authorities);
    }
  }
}
