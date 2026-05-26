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
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.fiat.model.SpinnakerAuthorities;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.filters.AuthRequestAttributes;
import com.netflix.spinnaker.gate.filters.AuthTypeResolver;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.security.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ApiTokenAuthenticationFilterTest {

  @Mock ApiTokenService apiTokenService;
  @Mock PermissionService permissionService;
  @Mock FiatPermissionEvaluator permissionEvaluator;
  @Mock AllowedAccountsSupport allowedAccountsSupport;

  ApiTokenProperties properties;
  ApiTokenAuthenticationFilter filter;

  private static final String TOKEN_PREFIX = "spk_";
  private static final String PLAINTEXT = "spk_abc123xyz789";
  private static final String TOKEN_ID = "token-id-42";
  private static final String PRINCIPAL = "bob@doordash.com";
  private static final String FUTURE_EXPIRY = Instant.now().plus(30, ChronoUnit.DAYS).toString();

  /** Pre-computed SHA-256 of PLAINTEXT so tests can verify the correct hash is looked up. */
  private static final String EXPECTED_HASH = ApiTokenHashing.sha256Hex(PLAINTEXT);

  @BeforeEach
  void setUp() {
    properties = new ApiTokenProperties();
    properties.setTokenPrefix(TOKEN_PREFIX);
    filter =
        new ApiTokenAuthenticationFilter(
            properties,
            apiTokenService,
            permissionService,
            permissionEvaluator,
            allowedAccountsSupport);
    // Lenient stub: rejection-path tests never reach the User-building branch, so strict stubbing
    // would fail. Tests that care about allowed accounts override this.
    lenient()
        .when(allowedAccountsSupport.filterAllowedAccounts(anyString(), anyCollection()))
        .thenReturn(List.of());
    SecurityContextHolder.clearContext();
  }

  /** Builds a {@link UserPermission.View} matching what {@code getPermission} would return. */
  private static UserPermission.View viewWith(boolean admin, String... roleNames) {
    UserPermission perm = new UserPermission().setId(PRINCIPAL);
    Set<Role> roles = new java.util.LinkedHashSet<>();
    for (String name : roleNames) {
      roles.add(new Role(name));
    }
    perm.setRoles(roles);
    UserPermission.View view = perm.getView();
    view.setAdmin(admin);
    return view;
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static TokenRecord record(
      String id, String principalId, String principalType, String expiresAt) {
    TokenRecord r = new TokenRecord();
    r.setId(id);
    r.setPrincipalId(principalId);
    r.setPrincipalType(principalType);
    r.setExpiresAt(expiresAt);
    return r;
  }

  // ---------------------------------------------------------------------------
  // Pass-through cases
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("no Authorization header — passes through without touching security context")
  void noAuthHeaderPassesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(apiTokenService);
  }

  @Test
  @DisplayName("OAuth2 Bearer token (no spk_ prefix) — passes through")
  void nonSpkBearerPassesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.some.jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(apiTokenService);
  }

  @Test
  @DisplayName(
      "unknown token hash (Redis miss) — passes through unauthenticated but is still marked as an"
          + " API-token request (so downstream filters skip session-flow logic)")
  void unknownHashPassesThrough() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH)).thenReturn(Optional.empty());

    MockHttpServletRequest request = requestWithBearer(PLAINTEXT);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    // Rejection paths still set IS_API_TOKEN/AUTH_TYPE; only PRINCIPAL_KIND/API_TOKEN_ID are
    // success-path-only.
    assertThat(request.getAttribute(AuthRequestAttributes.IS_API_TOKEN)).isEqualTo(Boolean.TRUE);
    assertThat(request.getAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE))
        .isEqualTo(AuthTypeResolver.TYPE_API_TOKEN);
    assertThat(request.getAttribute(AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE)).isNull();
    assertThat(request.getAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE)).isNull();
  }

  // ---------------------------------------------------------------------------
  // Authentication success cases
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "valid token — marks request with IS_API_TOKEN and the metrics attributes"
          + " (AUTH_TYPE, PRINCIPAL_KIND, API_TOKEN_ID)")
  void validTokenSetsRequestAttributes() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "SERVICE_ACCOUNT", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(false);

    MockHttpServletRequest request = requestWithBearer(PLAINTEXT);
    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(request.getAttribute(AuthRequestAttributes.IS_API_TOKEN)).isEqualTo(Boolean.TRUE);
    assertThat(request.getAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE))
        .isEqualTo(AuthTypeResolver.TYPE_API_TOKEN);
    // principalType lowercased so RequestMetricsFilter tag values stay in the fixed vocabulary
    assertThat(request.getAttribute(AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE))
        .isEqualTo("service_account");
    assertThat(request.getAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE))
        .isEqualTo(TOKEN_ID);
  }

  @Test
  @DisplayName(
      "valid token — MUST NOT call permissionService.login(): doing so re-resolves the principal"
          + " via Fiat's role providers and wipes the live session's roles for users whose roles"
          + " were loaded via loginWithRoles (e.g. OIDC users)")
  void validTokenDoesNotCallFiatLogin() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(permissionEvaluator.getPermission(PRINCIPAL)).thenReturn(viewWith(false, "deploy-team"));

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    verify(permissionService, never()).login(any());
    verify(permissionService, never()).loginWithRoles(any(), any());
  }

  // ---------------------------------------------------------------------------
  // Fiat-derived authorities — the core of the FiatAuthenticationConverter parity fix
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "valid token (Fiat enabled) — Authentication is a PreAuthenticatedAuthenticationToken"
          + " whose authorities are derived from UserPermission.View.toGrantedAuthorities()")
  void preAuthenticatedTokenWithFiatAuthorities() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(permissionEvaluator.getPermission(PRINCIPAL))
        .thenReturn(viewWith(true, "deploy-team", "ops"));

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isInstanceOf(PreAuthenticatedAuthenticationToken.class);
    assertThat(auth.getPrincipal()).isInstanceOf(User.class);
    assertThat(((User) auth.getPrincipal()).getUsername()).isEqualTo(PRINCIPAL);
    assertThat(auth.getCredentials()).isEqualTo("N/A");
    assertThat(auth.isAuthenticated()).isTrue();

    Set<String> authorityNames = AuthorityUtils.authorityListToSet(auth.getAuthorities());
    assertThat(authorityNames).contains(SpinnakerAuthorities.ADMIN_AUTHORITY.getAuthority());
    assertThat(authorityNames)
        .contains(SpinnakerAuthorities.forRoleName("deploy-team").getAuthority());
    assertThat(authorityNames).contains(SpinnakerAuthorities.forRoleName("ops").getAuthority());

    // touchLastUsedAsync fires on every successful resolution; piggy-back the verify here.
    verify(apiTokenService).touchLastUsedAsync(TOKEN_ID, EXPECTED_HASH);
  }

  @Test
  @DisplayName(
      "valid token (Fiat enabled) — principal is a fully-populated User with username, roles, and"
          + " allowedAccounts derived from AllowedAccountsSupport (so /auth/user returns a real"
          + " body and @SpinnakerUser resolves correctly)")
  void principalIsAFullyPopulatedUser() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(permissionEvaluator.getPermission(PRINCIPAL))
        .thenReturn(viewWith(false, "deploy-team", "ops"));
    when(allowedAccountsSupport.filterAllowedAccounts(eq(PRINCIPAL), anyCollection()))
        .thenReturn(java.util.List.of("prod-account", "stage-account"));

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getPrincipal()).isInstanceOf(User.class);
    User user = (User) auth.getPrincipal();
    assertThat(user.getUsername()).isEqualTo(PRINCIPAL);
    assertThat(user.getEmail()).isEqualTo(PRINCIPAL);
    assertThat(user.getRoles()).containsExactlyInAnyOrder("deploy-team", "ops");
    assertThat(user.getAllowedAccounts())
        .containsExactlyInAnyOrder("prod-account", "stage-account");

    // Role names (not Role.View) are passed through, matching header/OAuth2 auth convention.
    ArgumentCaptor<java.util.Collection<String>> rolesCaptor =
        ArgumentCaptor.forClass(java.util.Collection.class);
    verify(allowedAccountsSupport).filterAllowedAccounts(eq(PRINCIPAL), rolesCaptor.capture());
    assertThat(rolesCaptor.getValue()).containsExactlyInAnyOrder("deploy-team", "ops");
  }

  @Test
  @DisplayName(
      "valid token (Fiat enabled) — getPermission returns null → no Authentication set, request"
          + " falls through unauthenticated but is still marked as an API-token request")
  void nullFiatPermissionRejectsToken() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(permissionEvaluator.getPermission(PRINCIPAL)).thenReturn(null);

    MockHttpServletRequest request = requestWithBearer(PLAINTEXT);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    // IS_API_TOKEN/AUTH_TYPE must be set even on rejection so FiatSessionFilter and DPoP skip
    // their session-flow logic on what is unambiguously a token request.
    assertThat(request.getAttribute(AuthRequestAttributes.IS_API_TOKEN)).isEqualTo(Boolean.TRUE);
    assertThat(request.getAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE))
        .isEqualTo(AuthTypeResolver.TYPE_API_TOKEN);
    // PRINCIPAL_KIND / API_TOKEN_ID are success-path-only.
    assertThat(request.getAttribute(AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE)).isNull();
    assertThat(request.getAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE)).isNull();
    verify(chain).doFilter(request, response);
    verify(apiTokenService, never()).touchLastUsedAsync(any(), any());
  }

  @Test
  @DisplayName(
      "valid token (Fiat disabled) — sets an authenticated PreAuthenticatedAuthenticationToken"
          + " whose principal is a User (empty roles/authorities), and never consults Fiat"
          + " (preserves pre-Fiat behaviour)")
  void validTokenAuthenticatesWithFiatDisabled() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(false);

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull().isInstanceOf(PreAuthenticatedAuthenticationToken.class);
    assertThat(auth.isAuthenticated()).isTrue();

    // Principal must be a User (not a raw String) so @SpinnakerUser resolves correctly. With Fiat
    // disabled the User carries no roles/accounts.
    assertThat(auth.getPrincipal()).isInstanceOf(User.class);
    User user = (User) auth.getPrincipal();
    assertThat(user.getUsername()).isEqualTo(PRINCIPAL);
    assertThat(user.getRoles()).isEmpty();

    assertThat(auth.getAuthorities()).isEmpty();
    verify(permissionEvaluator, never()).getPermission(any());
  }

  @Test
  @DisplayName("non-expiring token (null expiresAt) — authenticates successfully")
  void nonExpiringTokenAuthenticates() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "SERVICE_ACCOUNT", null)));
    when(permissionService.isEnabled()).thenReturn(false);

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  // ---------------------------------------------------------------------------
  // X-Spinnaker-Token header (IAP-safe alternative)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("X-Spinnaker-Token header — authenticates successfully (IAP-safe path)")
  void xSpinnakerTokenAuthenticates() throws Exception {
    TokenRecord rec = record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY);
    when(apiTokenService.resolveByHash(EXPECTED_HASH)).thenReturn(Optional.of(rec));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, PLAINTEXT);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getName()).isEqualTo(PRINCIPAL);
    // Verify the request was wrapped to strip X-Spinnaker-Token before forwarding (so downstream
    // filters/MDC can't leak the plaintext token).
    verify(chain).doFilter(argThat(stripsXSpinnakerTokenHeader()), eq(response));
  }

  @Test
  @DisplayName("X-Spinnaker-Token takes precedence over Authorization header")
  void xSpinnakerTokenTakesPrecedenceOverBearer() throws Exception {
    TokenRecord rec = record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY);
    when(apiTokenService.resolveByHash(EXPECTED_HASH)).thenReturn(Optional.of(rec));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, PLAINTEXT);
    // Also set a non-spk bearer — should be ignored
    request.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.some.jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    verify(apiTokenService).resolveByHash(EXPECTED_HASH);
  }

  @Test
  @DisplayName("X-Spinnaker-Token without spk_ prefix — passes through unauthenticated")
  void xSpinnakerTokenWithoutPrefixPassesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN, "not-a-spk-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    // Wrapping is triggered by the header's presence, not by whether the token resolves — so even
    // a malformed token never leaks raw to downstream filters.
    verify(chain).doFilter(argThat(stripsXSpinnakerTokenHeader()), eq(response));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(apiTokenService);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static MockHttpServletRequest requestWithBearer(String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    return request;
  }

  /** Matcher: the forwarded request no longer exposes {@code X-Spinnaker-Token} (any casing). */
  private static org.mockito.ArgumentMatcher<HttpServletRequest> stripsXSpinnakerTokenHeader() {
    return req ->
        req != null
            && req.getHeader(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN) == null
            && req.getHeader(
                    ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN.toLowerCase(Locale.ROOT))
                == null
            && !Collections.list(req.getHeaderNames()).stream()
                .anyMatch(
                    n -> ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(n));
  }
}
