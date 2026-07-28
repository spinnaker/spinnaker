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

package com.netflix.spinnaker.gate.security.apitoken;

import com.netflix.spinnaker.gate.filters.AuthRequestAttributes;
import com.netflix.spinnaker.gate.filters.AuthTypeResolver;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests bearing a Spinnaker API token from {@code X-Spinnaker-Token: spk_…}
 * (preferred behind IAP, which rejects non-JWT {@code Authorization: Bearer} values) or {@code
 * Authorization: Bearer spk_…}. {@code X-Spinnaker-Token} is checked first.
 *
 * <p>Wired into a dedicated {@link
 * org.springframework.security.config.http.SessionCreationPolicy#STATELESS STATELESS} Spring
 * Security chain by {@code ApiTokenAuthConfigurerAdapter}, so token auth never writes to {@code
 * HttpSession} and cannot leak into cookie-only requests.
 *
 * <p>Publishes request attributes {@link AuthRequestAttributes#IS_API_TOKEN}, {@link
 * AuthTypeResolver#AUTH_TYPE_ATTRIBUTE}, and {@link AuthTypeResolver#PRINCIPAL_KIND_ATTRIBUTE} so
 * downstream session/DPoP filters can skip and {@code RequestMetricsFilter} can tag the request.
 */
@Slf4j
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

  /** Request attribute carrying the resolved token's id (UUID). */
  public static final String API_TOKEN_ID_ATTRIBUTE = "gate.apiToken.id";

  private final ApiTokenProperties properties;
  private final ApiTokenService apiTokenService;
  private final PermissionService permissionService;
  private final GateIdentityService identityService;
  private final AllowedAccountsSupport allowedAccountsSupport;

  public ApiTokenAuthenticationFilter(
      ApiTokenProperties properties,
      ApiTokenService apiTokenService,
      PermissionService permissionService,
      GateIdentityService identityService,
      AllowedAccountsSupport allowedAccountsSupport) {
    this.properties = properties;
    this.apiTokenService = apiTokenService;
    this.permissionService = permissionService;
    this.identityService = identityService;
    this.allowedAccountsSupport = allowedAccountsSupport;
  }

  public static final String HEADER_X_SPINNAKER_TOKEN = "X-Spinnaker-Token";

  /** RFC 7235 §2.1 auth-scheme prefix (including trailing space) for {@code Authorization}. */
  static final String BEARER_SCHEME = "Bearer ";

  /**
   * Case-insensitive RFC 7235 §2.1 match of the {@code Bearer } auth-scheme prefix. Shared with
   * {@code ApiTokenAuthConfigurerAdapter.ApiTokenRequestMatcher} so the routing layer and the
   * filter can't drift on what counts as a Bearer header.
   */
  static boolean hasBearerScheme(String authHeader) {
    return authHeader != null
        && authHeader.regionMatches(true, 0, BEARER_SCHEME, 0, BEARER_SCHEME.length());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    String plaintext = extractToken(request);

    // SECURITY: kork's AuthenticatedRequestFilter copies X-Spinnaker-* headers into MDC and then
    // re-emits MDC entries as outbound headers on every downstream call, which would leak the
    // plaintext token to downstream service logs. Clear it from MDC and wrap the request so it
    // can't be re-read.
    if (request.getHeader(HEADER_X_SPINNAKER_TOKEN) != null) {
      MDC.remove(HEADER_X_SPINNAKER_TOKEN.toUpperCase(Locale.ROOT));
      request = new TokenHeaderStrippingRequestWrapper(request);
    }

    if (plaintext == null) {
      chain.doFilter(request, response);
      return;
    }

    // Mark as a token request before we know whether it resolves: downstream filters / DPoP key off
    // these attributes to skip session-flow logic, and the metrics filter uses them for tagging.
    request.setAttribute(AuthRequestAttributes.IS_API_TOKEN, Boolean.TRUE);
    request.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, AuthTypeResolver.TYPE_API_TOKEN);

    String tokenHash = ApiTokenHashing.sha256Hex(plaintext);

    Optional<TokenRecord> resolved = apiTokenService.resolveByHash(tokenHash);
    if (resolved.isEmpty()) {
      log.debug("API token not found for provided hash");
      chain.doFilter(request, response);
      return;
    }

    TokenRecord record = resolved.get();
    String principalId = record.getPrincipalId();
    String principalKind = normalisePrincipalKind(record.getPrincipalType());

    PreAuthenticatedAuthenticationToken authentication;
    String identityToken = null;
    if (permissionService.isEnabled()) {
      // Edge token-exchange: resolve the principal's roles via kork-roles at request time (backed
      // by GateIdentityService's short-TTL per-principal cache to absorb high-volume CI traffic),
      // then mint a signed identity token. allowAnonymous keeps any stale X-SPINNAKER-USER in MDC
      // from leaking to the role providers, since we run before the SecurityContext is populated.
      Set<String> roles;
      if ("SERVICE_ACCOUNT".equalsIgnoreCase(record.getPrincipalType())) {
        // Service accounts are owned by Front50: resolve their roles from the SA's memberOf (merged
        // via the same resolver used by login / run-as), not from the user role provider — which
        // never knows a service account. This restores the Fiat behaviour where SA tokens carry the
        // roles defined on the service account.
        roles = new LinkedHashSet<>(permissionService.resolveServiceAccountRoles(principalId));
      } else {
        roles =
            AuthenticatedRequest.allowAnonymous(
                () -> new LinkedHashSet<>(identityService.rolesFor(principalId)));
        // EXTERNAL fallback: with no role provider, roles can't be resolved from the principal id
        // at
        // exchange time (there's no live login session here), so live resolution comes back empty.
        // Recover the roles captured on the token at creation. Gated on there being no provider so
        // provider-backed deployments never serve a stale snapshot (they resolve live, which keeps
        // revocation/refresh authoritative).
        if (roles.isEmpty() && !identityService.hasUserRolesResolver()) {
          List<String> snapshot = record.getRoles();
          if (snapshot != null && !snapshot.isEmpty()) {
            roles = new LinkedHashSet<>(snapshot);
          }
        }
      }
      User user = buildUserPrincipal(principalId, roles);
      authentication =
          new PreAuthenticatedAuthenticationToken(user, "N/A", buildAuthorities(roles));
      identityToken = identityService.mintToken(principalId, roles);
    } else {
      // Authz disabled: still produce a User principal so /auth/user has a body and @SpinnakerUser
      // resolves; roles/allowedAccounts are empty.
      User user = buildUserPrincipal(principalId, List.of());
      authentication = new PreAuthenticatedAuthenticationToken(user, "N/A", List.of());
    }
    SecurityContextHolder.getContext().setAuthentication(authentication);
    if (identityToken != null) {
      // Propagate the minted token downstream (kork's outbound interceptor re-emits MDC
      // X-SPINNAKER-* headers); the propagation filter sees it set and skips re-minting.
      AuthenticatedRequest.set(Header.IDENTITY_TOKEN, identityToken);
    }

    request.setAttribute(AuthTypeResolver.PRINCIPAL_KIND_ATTRIBUTE, principalKind);
    request.setAttribute(API_TOKEN_ID_ATTRIBUTE, record.getId());

    apiTokenService.touchLastUsedAsync(record.getId(), tokenHash);

    log.debug("Authenticated API token request as principal={}", principalId);
    chain.doFilter(request, response);
  }

  /**
   * Build the {@link User} principal so the {@code @SpinnakerUser} argument resolver populates
   * controller params correctly. (A raw String principal would silently resolve to {@code null}.)
   */
  private User buildUserPrincipal(String principalId, Collection<String> roleNames) {
    User user = new User();
    user.setUsername(principalId);
    user.setEmail(principalId);
    user.setRoles(roleNames);
    user.setAllowedAccounts(allowedAccountsSupport.filterAllowedAccounts(principalId, roleNames));
    return user;
  }

  /** ROLE_* authorities for the resolved roles, plus admin / account-manager flags. */
  private List<GrantedAuthority> buildAuthorities(Set<String> roles) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String role : roles) {
      authorities.add(SpinnakerAuthorities.forRoleName(role));
    }
    if (identityService.isAdmin(roles)) {
      authorities.add(SpinnakerAuthorities.ADMIN_AUTHORITY);
    }
    if (identityService.isAccountManager(roles)) {
      authorities.add(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
    }
    return authorities;
  }

  /**
   * Translates {@code principalType} to the closed lowercase metric-tag vocabulary defined by
   * {@link AuthTypeResolver}. Unknown values map to {@link AuthTypeResolver#PRINCIPAL_KIND_UNKNOWN}
   * to keep tag cardinality bounded.
   */
  private static String normalisePrincipalKind(String principalType) {
    if (principalType == null) {
      return AuthTypeResolver.PRINCIPAL_KIND_UNKNOWN;
    }
    return switch (principalType.trim().toUpperCase(Locale.ROOT)) {
      case "USER" -> AuthTypeResolver.PRINCIPAL_KIND_USER;
      case "SERVICE_ACCOUNT" -> AuthTypeResolver.PRINCIPAL_KIND_SERVICE_ACCOUNT;
      default -> AuthTypeResolver.PRINCIPAL_KIND_UNKNOWN;
    };
  }

  /**
   * Extracts the plaintext token from {@code X-Spinnaker-Token} (preferred, IAP-safe) or {@code
   * Authorization: Bearer spk_…}. Only the {@code X-Spinnaker-Token} path needs the strip-and-wrap
   * dance in {@link #doFilterInternal} — kork only auto-copies {@code X-Spinnaker-*} headers into
   * MDC.
   *
   * @return the raw token value, or {@code null} if no Spinnaker token header is present
   */
  private String extractToken(HttpServletRequest request) {
    String xToken = request.getHeader(HEADER_X_SPINNAKER_TOKEN);
    if (xToken != null && xToken.startsWith(properties.getTokenPrefix())) {
      return xToken;
    }
    String authHeader = request.getHeader("Authorization");
    // RFC 7235 §2.1: the auth-scheme token is case-insensitive ("Bearer"/"bearer"/"BEARER" are all
    // valid). The opaque token (spk_…) itself is case-sensitive and stays so.
    if (hasBearerScheme(authHeader)) {
      String candidate = authHeader.substring(BEARER_SCHEME.length());
      if (candidate.startsWith(properties.getTokenPrefix())) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Wraps the request and hides the {@code X-Spinnaker-Token} header from all downstream code. This
   * prevents it from being re-read by any filter that might copy it back into MDC or forward it to
   * downstream services.
   */
  private static final class TokenHeaderStrippingRequestWrapper extends HttpServletRequestWrapper {
    TokenHeaderStrippingRequestWrapper(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      if (HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(name)) return null;
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(name)) return Collections.emptyEnumeration();
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(
          Collections.list(super.getHeaderNames()).stream()
              .filter(n -> !HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(n))
              .collect(Collectors.toList()));
    }
  }
}
