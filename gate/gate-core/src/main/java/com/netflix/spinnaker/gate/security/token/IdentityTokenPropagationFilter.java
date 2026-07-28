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

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the authenticated caller's resolved Spinnaker identity at Gate's edge for the
 * duration of the request. It does two things, both driven by the caller's locally-resolved roles:
 *
 * <ol>
 *   <li><b>Edge authorities:</b> augments the {@code SecurityContext} authentication with the
 *       Spinnaker authorities ({@code ROLE_<role>} plus the admin / account-manager authorities)
 *       derived from the resolved roles and {@code authz.gate.*-roles} config. Gate is the token
 *       <em>minter</em>, not a verifier, so its edge session is built by the login mechanism and
 *       (unlike a downstream service populated by {@link
 *       com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter}) does not
 *       otherwise carry these authorities. Without them Gate's own method security — the admin
 *       bypass in {@code hasPermission(...)} and {@code @spinnakerPermissionEvaluator.isAdmin()} —
 *       cannot fire, so e.g. an admin's {@code GET /applications} {@code @PostFilter} would deny
 *       every application.
 *   <li><b>Token propagation:</b> mints the short-lived signed identity token and places it in the
 *       MDC under {@link Header#IDENTITY_TOKEN} so kork's outbound request interceptor propagates
 *       it to downstream services (which verify the signature and populate authorities from it).
 * </ol>
 *
 * <p>This runs inside Gate's Spring Security filter chain (after authentication is established) so
 * it uniformly covers every auth mechanism. It only acts on edge sessions (no inbound identity
 * token); service-to-service calls that already carry a token are owned by {@link
 * GateIdentityTokenInboundFilter}. The augmented authentication is scoped to the request and the
 * original is restored afterwards, so the persisted session authentication is never mutated.
 */
public class IdentityTokenPropagationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(IdentityTokenPropagationFilter.class);

  private final GateIdentityService identityService;

  public IdentityTokenPropagationFilter(GateIdentityService identityService) {
    this.identityService = identityService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean tokenSet = false;
    Authentication originalAuthentication = null;
    boolean authenticationAugmented = false;

    // Edge sessions only: if the request already carries a token, GateIdentityTokenInboundFilter
    // owns the SecurityContext, and we must not overwrite a token an upstream already supplied.
    if (request.getHeader(Header.IDENTITY_TOKEN.getHeader()) == null) {
      try {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = subject(authentication);
        if (username != null) {
          // The authoritative, locally-resolved roles captured at login (cached). This is the same
          // source the identity token is minted from, so edge authorities and the token agree. The
          // live authentication's asserted roles are passed as the cache-miss fallback so an
          // aged-out entry can be rebuilt from the session principal — essential in EXTERNAL mode
          // (no role provider), where roles exist only on the principal and can't be re-resolved.
          List<String> roles =
              List.copyOf(identityService.rolesFor(username, assertedRoles(authentication)));

          originalAuthentication = authentication;
          authenticationAugmented = establishEdgeAuthorities(authentication, roles);

          if (identityService.isMinterAvailable()) {
            String token = identityService.mintToken(username, roles);
            if (token != null) {
              AuthenticatedRequest.set(Header.IDENTITY_TOKEN, token);
              tokenSet = true;
            }
          }
        }
      } catch (RuntimeException e) {
        log.debug("Unable to establish edge identity for propagation", e);
      }
    }

    try {
      chain.doFilter(request, response);
    } finally {
      if (authenticationAugmented) {
        // Keep the augmentation request-scoped; never mutate the persisted session authentication.
        SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
      }
      if (tokenSet) {
        org.slf4j.MDC.remove(Header.IDENTITY_TOKEN.getHeader());
      }
    }
  }

  /**
   * Augments the current authentication with the Spinnaker authorities ({@code ROLE_<role>} plus
   * the admin / account-manager authorities) derived from the resolved roles, preserving the
   * original principal, credentials and details. Returns {@code true} when the {@code
   * SecurityContext} was replaced (i.e. new authorities were added), {@code false} when nothing
   * changed.
   */
  private boolean establishEdgeAuthorities(Authentication authentication, List<String> roles) {
    Set<GrantedAuthority> merged = new LinkedHashSet<>(authentication.getAuthorities());
    boolean changed = false;
    for (String role : roles) {
      changed |= merged.add(SpinnakerAuthorities.forRoleName(role));
    }
    if (identityService.isAdmin(roles)) {
      changed |= merged.add(SpinnakerAuthorities.ADMIN_AUTHORITY);
    }
    if (identityService.isAccountManager(roles)) {
      changed |= merged.add(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
    }
    if (!changed) {
      return false;
    }
    PreAuthenticatedAuthenticationToken augmented =
        new PreAuthenticatedAuthenticationToken(
            authentication.getPrincipal(), authentication.getCredentials(), merged);
    augmented.setDetails(authentication.getDetails());
    SecurityContextHolder.getContext().setAuthentication(augmented);
    return true;
  }

  /**
   * The roles asserted by the live authentication, used as the cache-miss fallback for role
   * resolution. Drawn from the Spinnaker {@link User} principal (the roles the login mechanism
   * attached, e.g. OIDC/SAML claims) unioned with any {@code ROLE_<role>} authorities, so it covers
   * every auth mechanism. In EXTERNAL mode this is the only source from which an aged-out cache
   * entry can be rebuilt.
   */
  private static List<String> assertedRoles(Authentication authentication) {
    Set<String> roles = new LinkedHashSet<>();
    Object principal = authentication.getPrincipal();
    if (principal instanceof User user) {
      roles.addAll(user.getRoles());
    }
    roles.addAll(SpinnakerAuthorities.getRoles(authentication));
    return List.copyOf(roles);
  }

  private static String subject(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }
    String name = authentication.getName();
    return (name == null || name.isBlank()) ? null : name;
  }
}
