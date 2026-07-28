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

import com.netflix.spinnaker.gate.filters.AuthRequestAttributes;
import com.netflix.spinnaker.gate.filters.AuthTypeResolver;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
  @Mock GateIdentityService identityService;
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
            identityService,
            allowedAccountsSupport);
    // Lenient stub: rejection-path tests never reach the User-building branch, so strict stubbing
    // would fail. Tests that care about allowed accounts override this.
    lenient()
        .when(allowedAccountsSupport.filterAllowedAccounts(anyString(), anyCollection()))
        .thenReturn(List.of());
    SecurityContextHolder.clearContext();
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
          + " via the role providers and wipes the live session's roles for users whose roles"
          + " were loaded via loginWithRoles (e.g. OIDC users)")
  void validTokenDoesNotCallLogin() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of("deploy-team"));

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    verify(permissionService, never()).login(any());
    verify(permissionService, never()).loginWithRoles(any(), any());
  }

  // ---------------------------------------------------------------------------
  // Edge token-exchange — roles resolved via kork-roles (GateIdentityService)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "valid token (authz enabled) — Authentication is a PreAuthenticatedAuthenticationToken"
          + " whose authorities are derived from the edge-resolved roles + admin flag")
  void preAuthenticatedTokenWithResolvedAuthorities() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of("deploy-team", "ops"));
    when(identityService.isAdmin(anyCollection())).thenReturn(true);

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
      "valid token (authz enabled) — principal is a fully-populated User with username, roles, and"
          + " allowedAccounts derived from AllowedAccountsSupport (so /auth/user returns a real"
          + " body and @SpinnakerUser resolves correctly)")
  void principalIsAFullyPopulatedUser() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of("deploy-team", "ops"));
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
      "valid token (authz enabled) — edge-resolved roles are empty → still authenticates with an"
          + " empty-authority principal (the departed/unknown-principal rejection is"
          + " ApiTokenService's job, not the filter's)")
  void emptyResolvedRolesStillAuthenticate() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of());

    MockHttpServletRequest request = requestWithBearer(PLAINTEXT);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull().isInstanceOf(PreAuthenticatedAuthenticationToken.class);
    assertThat(((User) auth.getPrincipal()).getUsername()).isEqualTo(PRINCIPAL);
    assertThat(auth.getAuthorities()).isEmpty();
    assertThat(request.getAttribute(AuthRequestAttributes.IS_API_TOKEN)).isEqualTo(Boolean.TRUE);
    verify(apiTokenService).touchLastUsedAsync(TOKEN_ID, EXPECTED_HASH);
  }

  @Test
  @DisplayName(
      "EXTERNAL (no role provider): empty live resolution falls back to the token's stored role"
          + " snapshot, so the token authenticates with its captured roles")
  void externalFallsBackToStoredRoles() throws Exception {
    TokenRecord rec = record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY);
    rec.setRoles(List.of("deploy-team", "ops"));
    when(apiTokenService.resolveByHash(EXPECTED_HASH)).thenReturn(Optional.of(rec));
    when(permissionService.isEnabled()).thenReturn(true);
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of());
    when(identityService.hasUserRolesResolver()).thenReturn(false);

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Set<String> authorityNames = AuthorityUtils.authorityListToSet(auth.getAuthorities());
    assertThat(authorityNames)
        .contains(
            SpinnakerAuthorities.forRoleName("deploy-team").getAuthority(),
            SpinnakerAuthorities.forRoleName("ops").getAuthority());
    assertThat(((User) auth.getPrincipal()).getRoles())
        .containsExactlyInAnyOrder("deploy-team", "ops");
  }

  @Test
  @DisplayName(
      "SERVICE_ACCOUNT token (authz enabled) — roles come from the SA's Front50 memberOf via"
          + " PermissionService, not from the user role provider")
  void serviceAccountTokenResolvesRolesFromFront50() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "SERVICE_ACCOUNT", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(true);
    when(permissionService.resolveServiceAccountRoles(PRINCIPAL))
        .thenReturn(Set.of("deploy-team", "ops"));

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Set<String> authorityNames = AuthorityUtils.authorityListToSet(auth.getAuthorities());
    assertThat(authorityNames)
        .contains(
            SpinnakerAuthorities.forRoleName("deploy-team").getAuthority(),
            SpinnakerAuthorities.forRoleName("ops").getAuthority());
    assertThat(((User) auth.getPrincipal()).getRoles())
        .containsExactlyInAnyOrder("deploy-team", "ops");
    // The service-account path must not consult the user (group-membership) role resolver.
    verify(identityService, never()).rolesFor(anyString());
  }

  @Test
  @DisplayName(
      "provider-backed (resolver present): empty live resolution does NOT fall back to the stored"
          + " snapshot — provider resolution is authoritative so revoked roles aren't re-granted")
  void providerModeIgnoresStoredRoles() throws Exception {
    TokenRecord rec = record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY);
    rec.setRoles(List.of("deploy-team"));
    when(apiTokenService.resolveByHash(EXPECTED_HASH)).thenReturn(Optional.of(rec));
    when(permissionService.isEnabled()).thenReturn(true);
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of());
    when(identityService.hasUserRolesResolver()).thenReturn(true);

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getAuthorities()).isEmpty();
    assertThat(((User) auth.getPrincipal()).getRoles()).isEmpty();
  }

  @Test
  @DisplayName(
      "valid token (authz disabled) — sets an authenticated PreAuthenticatedAuthenticationToken"
          + " whose principal is a User (empty roles/authorities), and never consults the identity"
          + " service (preserves pre-authz behaviour)")
  void validTokenAuthenticatesWithAuthzDisabled() throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(false);

    filter.doFilterInternal(
        requestWithBearer(PLAINTEXT), new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull().isInstanceOf(PreAuthenticatedAuthenticationToken.class);
    assertThat(auth.isAuthenticated()).isTrue();

    // Principal must be a User (not a raw String) so @SpinnakerUser resolves correctly. With authz
    // disabled the User carries no roles/accounts.
    assertThat(auth.getPrincipal()).isInstanceOf(User.class);
    User user = (User) auth.getPrincipal();
    assertThat(user.getUsername()).isEqualTo(PRINCIPAL);
    assertThat(user.getRoles()).isEmpty();

    assertThat(auth.getAuthorities()).isEmpty();
    verify(identityService, never()).rolesFor(any());
  }

  // ---------------------------------------------------------------------------
  // Case-insensitive Bearer scheme (RFC 7235 §2.1: auth-scheme is case-insensitive)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Authorization: 'Bearer spk_…' (canonical casing) — authenticates successfully")
  void bearerCanonicalCaseAuthenticates() throws Exception {
    assertBearerPrefixAuthenticates("Bearer ");
  }

  @Test
  @DisplayName("Authorization: 'bearer spk_…' (lowercase scheme) — authenticates successfully")
  void bearerLowercaseAuthenticates() throws Exception {
    assertBearerPrefixAuthenticates("bearer ");
  }

  @Test
  @DisplayName("Authorization: 'BEARER spk_…' (uppercase scheme) — authenticates successfully")
  void bearerUppercaseAuthenticates() throws Exception {
    assertBearerPrefixAuthenticates("BEARER ");
  }

  /**
   * Shared assertion for the three Bearer-casing tests: a token presented with the given prefix
   * resolves to the same authenticated principal, proving the scheme match is case-insensitive
   * while the opaque {@code spk_} token prefix is preserved verbatim downstream.
   */
  private void assertBearerPrefixAuthenticates(String bearerPrefix) throws Exception {
    when(apiTokenService.resolveByHash(EXPECTED_HASH))
        .thenReturn(Optional.of(record(TOKEN_ID, PRINCIPAL, "USER", FUTURE_EXPIRY)));
    when(permissionService.isEnabled()).thenReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", bearerPrefix + PLAINTEXT);

    filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getName()).isEqualTo(PRINCIPAL);
    verify(apiTokenService).resolveByHash(EXPECTED_HASH);
  }

  @Test
  @DisplayName("Authorization: 'Bearer SPK_…' (uppercased token prefix) — passes through")
  void bearerWithUppercasedTokenPrefixPassesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    // Bearer scheme case is permissive; the spk_ token prefix is case-sensitive and must not match.
    request.addHeader("Authorization", "Bearer SPK_abc123xyz789");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = spy(new MockFilterChain());

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(apiTokenService);
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
