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

package com.netflix.spinnaker.gate.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Verifies the edge behavior of {@link IdentityTokenPropagationFilter}: it augments Gate's edge
 * {@code SecurityContext} with the caller's resolved Spinnaker authorities for the duration of the
 * request (so Gate's own method security can fire), mints the identity token into the MDC for
 * downstream propagation, and never touches a request that already carries an inbound token.
 */
class IdentityTokenPropagationFilterTest {

  private final GateIdentityService identityService = mock(GateIdentityService.class);

  private static final String USER = "alice@doordash.com";
  private static final String TOKEN_HEADER = Header.IDENTITY_TOKEN.getHeader();

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    MDC.remove(TOKEN_HEADER);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.remove(TOKEN_HEADER);
  }

  private static List<String> authorities(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());
  }

  private void authenticatedAs(String username, String... roleAuthorities) {
    PreAuthenticatedAuthenticationToken auth =
        new PreAuthenticatedAuthenticationToken(
            username,
            "N/A",
            java.util.Arrays.stream(roleAuthorities)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toList()));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  @DisplayName(
      "edge admin session: SecurityContext gains ROLE_<role> + admin for the request, restored after")
  void adminEdgeSessionGainsAdminAuthorityScopedToRequest() throws Exception {
    authenticatedAs(USER);
    when(identityService.rolesFor(eq(USER), anyList()))
        .thenReturn(Set.of("spinnaker-admins", "deploy-team"));
    when(identityService.isAdmin(anyList())).thenReturn(true);
    when(identityService.isAccountManager(anyList())).thenReturn(false);
    when(identityService.isMinterAvailable()).thenReturn(false);

    AtomicReference<Authentication> during = new AtomicReference<>();
    FilterChain chain =
        (req, res) -> during.set(SecurityContextHolder.getContext().getAuthentication());

    new IdentityTokenPropagationFilter(identityService)
        .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    // During the request the augmented authentication carries the admin + role authorities.
    assertThat(authorities(during.get()))
        .contains(SpinnakerAuthorities.ADMIN, "ROLE_spinnaker-admins", "ROLE_deploy-team");
    assertThat(SpinnakerAuthorities.isAdmin(during.get())).isTrue();

    // Afterwards the original (un-augmented) authentication is restored.
    Authentication restored = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authorities(restored)).doesNotContain(SpinnakerAuthorities.ADMIN);
    assertThat(SpinnakerAuthorities.isAdmin(restored)).isFalse();
  }

  @Test
  @DisplayName("edge non-admin session: gains ROLE_<role> authorities but not the admin authority")
  void nonAdminEdgeSessionGainsRolesButNotAdmin() throws Exception {
    authenticatedAs(USER);
    when(identityService.rolesFor(eq(USER), anyList())).thenReturn(Set.of("deploy-team"));
    when(identityService.isAdmin(anyList())).thenReturn(false);
    when(identityService.isAccountManager(anyList())).thenReturn(false);
    when(identityService.isMinterAvailable()).thenReturn(false);

    AtomicReference<Authentication> during = new AtomicReference<>();
    FilterChain chain =
        (req, res) -> during.set(SecurityContextHolder.getContext().getAuthentication());

    new IdentityTokenPropagationFilter(identityService)
        .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(authorities(during.get())).contains("ROLE_deploy-team");
    assertThat(authorities(during.get())).doesNotContain(SpinnakerAuthorities.ADMIN);
    assertThat(SpinnakerAuthorities.isAdmin(during.get())).isFalse();
  }

  @Test
  @DisplayName("requests carrying an inbound identity token are left untouched")
  void inboundTokenRequestIsLeftUntouched() throws Exception {
    Authentication original =
        new PreAuthenticatedAuthenticationToken(
            "svc", "N/A", List.of(new SimpleGrantedAuthority("ROLE_existing")));
    SecurityContextHolder.getContext().setAuthentication(original);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, "inbound.jwt");

    AtomicReference<Authentication> during = new AtomicReference<>();
    AtomicReference<String> mdcDuring = new AtomicReference<>();
    FilterChain chain =
        (req, res) -> {
          during.set(SecurityContextHolder.getContext().getAuthentication());
          mdcDuring.set(MDC.get(TOKEN_HEADER));
        };

    new IdentityTokenPropagationFilter(identityService)
        .doFilter(request, new MockHttpServletResponse(), chain);

    // The inbound filter owns this request: the SecurityContext is untouched and no token is
    // minted.
    assertThat(during.get()).isSameAs(original);
    assertThat(mdcDuring.get()).isNull();
    verify(identityService, never()).rolesFor(any(), anyList());
    verify(identityService, never()).mintToken(any(), anyList());
  }

  @Test
  @DisplayName("identity token is minted into the MDC for the request when a minter is available")
  void mintsTokenIntoMdcWhenMinterAvailable() throws Exception {
    authenticatedAs(USER);
    when(identityService.rolesFor(eq(USER), anyList())).thenReturn(Set.of("deploy-team"));
    lenient().when(identityService.isAdmin(anyList())).thenReturn(false);
    lenient().when(identityService.isAccountManager(anyList())).thenReturn(false);
    when(identityService.isMinterAvailable()).thenReturn(true);
    when(identityService.mintToken(eq(USER), anyList())).thenReturn("signed.jwt");

    AtomicReference<String> mdcDuring = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuring.set(MDC.get(TOKEN_HEADER));

    new IdentityTokenPropagationFilter(identityService)
        .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    // The minted token is visible in the MDC during the request and cleaned up afterwards.
    assertThat(mdcDuring.get()).isEqualTo("signed.jwt");
    assertThat(MDC.get(TOKEN_HEADER)).isNull();
  }

  @Test
  @DisplayName("anonymous edge session is not augmented and mints no token")
  void anonymousSessionIsIgnored() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(SpinnakerAuthorities.ANONYMOUS_AUTHORITY)));

    AtomicReference<String> mdcDuring = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuring.set(MDC.get(TOKEN_HEADER));

    new IdentityTokenPropagationFilter(identityService)
        .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(mdcDuring.get()).isNull();
    verify(identityService, never()).rolesFor(any(), anyList());
    verify(identityService, never()).mintToken(any(), anyList());
  }

  @Test
  @DisplayName(
      "passes the live principal's asserted roles to rolesFor so an EXTERNAL cache miss can be"
          + " rebuilt from the session")
  void passesAssertedRolesFromPrincipalToRolesFor() throws Exception {
    authenticatedAs(USER, "ROLE_deploy-team", "ROLE_ops");
    when(identityService.rolesFor(eq(USER), anyList())).thenReturn(Set.of("deploy-team", "ops"));
    lenient().when(identityService.isAdmin(anyList())).thenReturn(false);
    lenient().when(identityService.isAccountManager(anyList())).thenReturn(false);
    when(identityService.isMinterAvailable()).thenReturn(false);

    FilterChain chain = (req, res) -> {};
    new IdentityTokenPropagationFilter(identityService)
        .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(identityService).rolesFor(eq(USER), captor.capture());
    assertThat(captor.getValue()).contains("deploy-team", "ops");
  }
}
