/*
 * Copyright 2026 Spinnaker Contributors
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
package com.netflix.spinnaker.gate.ratelimit;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Resolves who is making the request. This runs after the spring security filter chain, so every
 * supported authentication mechanism (basic, ldap, header, x509, oauth2/oidc, saml, api tokens) has
 * already put its {@link Authentication} in the {@link SecurityContextHolder}.
 *
 * <p>All anonymous callers share a single bucket. Keying them by IP or by any client-supplied value
 * would let a caller obtain a fresh bucket per request by changing it.
 */
@Slf4j
public class DefaultRateLimitPrincipalResolver implements RateLimitPrincipalResolver {

  private final List<RequestIdentityExtractor> requestIdentityExtractors;

  private final PermissionService permissionService;

  public DefaultRateLimitPrincipalResolver(List<RequestIdentityExtractor> extractors) {
    this(extractors, null);
  }

  /**
   * @param permissionService when present, also supplies the caller's Fiat roles (including groups
   *     synced from an external provider), which are not necessarily granted authorities
   */
  public DefaultRateLimitPrincipalResolver(
      List<RequestIdentityExtractor> extractors, @Nullable PermissionService permissionService) {
    this.requestIdentityExtractors =
        Objects.requireNonNullElseGet(extractors, Collections::emptyList);
    this.permissionService = permissionService;
  }

  @Override
  public Optional<String> resolve(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null) {
      return extractIdentity(request);
    }

    if (authentication instanceof AnonymousAuthenticationToken) {
      return Optional.of(RateLimitPrincipalProvider.ANONYMOUS);
    }

    String name =
        authentication.getPrincipal() instanceof UserDetails userDetails
            ? userDetails.getUsername()
            : authentication.getName();

    if (name == null || name.isBlank()) {
      return Optional.of(RateLimitPrincipalProvider.ANONYMOUS);
    }

    return Optional.of(name);
  }

  @Override
  public Collection<String> resolveRoles(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return List.of();
    }

    Set<String> roles = new LinkedHashSet<>(SpinnakerAuthorities.getRoles(authentication));
    if (SpinnakerAuthorities.isAdmin(authentication)) {
      roles.add(SpinnakerAuthorities.ADMIN);
    }
    roles.addAll(fiatRoles(authentication));
    return roles;
  }

  private Set<String> fiatRoles(Authentication authentication) {
    if (permissionService == null || authentication.getName() == null) {
      return Set.of();
    }

    // Never let a role lookup failure turn into a failed request: fall back to the default tier.
    try {
      return permissionService.getRoles(authentication.getName()).stream()
          .map(Role.View::getName)
          .collect(Collectors.toSet());
    } catch (Exception e) {
      log.warn("Unable to look up roles for rate limiting, using default capacity", e);
      return Set.of();
    }
  }

  private Optional<String> extractIdentity(HttpServletRequest request) {
    for (RequestIdentityExtractor extractor : requestIdentityExtractors) {
      if (!extractor.supports(request)) {
        continue;
      }

      try {
        String identity = extractor.extractIdentity(request);
        if (identity != null) {
          return Optional.of(identity);
        }
      } catch (Throwable t) {
        log.warn(
            "Failure extracting identity from RequestIdentityExtractor {}",
            extractor.getClass().getSimpleName(),
            t);
      }
    }

    return Optional.empty();
  }
}
