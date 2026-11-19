/*
 * Copyright 2022 Apple, Inc.
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

package com.netflix.spinnaker.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Constants and utilities for working with Spring Security GrantedAuthority objects specific to
 * Spinnaker and Fiat. Spinnaker-specific roles are represented here as granted authorities with the
 * {@code SPINNAKER_} prefix.
 */
public class SpinnakerAuthorities {
  private static final String ROLE_PREFIX = "ROLE_";
  private static final String SPINNAKER_PREFIX = "SPINNAKER_";
  private static final String SCOPE_PREFIX = "SCOPE_";

  /** Authorities Spring Security's OAuth2 and OIDC user services grant to every user. */
  private static final Set<String> PROVIDER_AUTHORITIES = Set.of("OAUTH2_USER", "OIDC_USER");

  public static final String ADMIN = "SPINNAKER_ADMIN";

  /** Granted authority for Spinnaker administrators. */
  public static final GrantedAuthority ADMIN_AUTHORITY = new SimpleGrantedAuthority(ADMIN);

  /** Granted authority for anonymous users. */
  public static final GrantedAuthority ANONYMOUS_AUTHORITY = forRoleName("ANONYMOUS");

  /** Creates a granted authority corresponding to the provided name of a role. */
  @Nonnull
  public static GrantedAuthority forRoleName(@Nonnull String role) {
    return new SimpleGrantedAuthority(ROLE_PREFIX + role);
  }

  /**
   * Creates the granted authorities of a user: one authority per (bare) role and one {@code
   * ALLOWED_ACCOUNT_} authority per allowed account.
   */
  @Nonnull
  public static List<GrantedAuthority> forRolesAndAccounts(
      @Nullable Collection<String> roles, @Nullable Collection<String> allowedAccounts) {
    Stream<GrantedAuthority> roleAuthorities =
        roles == null
            ? Stream.empty()
            : roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(SimpleGrantedAuthority::new);
    return Stream.concat(
            roleAuthorities,
            AllowedAccountsAuthorities.buildAllowedAccounts(allowedAccounts).stream())
        .collect(Collectors.toList());
  }

  /** Checks if the given user is a Spinnaker admin. */
  public static boolean isAdmin(@Nullable Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().contains(SpinnakerAuthorities.ADMIN_AUTHORITY);
  }

  /** Checks if the given user has the provided role. */
  public static boolean hasRole(@Nullable Authentication authentication, @Nonnull String role) {
    return authentication != null && streamRoles(authentication).anyMatch(role::equals);
  }

  /** Checks if the given user has any of the provided roles. */
  public static boolean hasAnyRole(
      @Nullable Authentication authentication, @Nonnull Collection<String> roles) {
    return authentication != null && streamRoles(authentication).anyMatch(roles::contains);
  }

  /** Gets the list of roles assigned to the given user. */
  @Nonnull
  public static List<String> getRoles(@Nullable Authentication authentication) {
    if (authentication == null) {
      return List.of();
    }
    return streamRoles(authentication).distinct().collect(Collectors.toList());
  }

  /**
   * Gets the names of the roles of the given user as they are known to the identity provider and to
   * Fiat: bare role authorities and {@code ROLE_}-prefixed authorities, without the prefix.
   * Authorities that do not represent a role (allowed accounts, {@code SPINNAKER_}, OAuth2 scopes
   * and the default OAuth2/OIDC user authorities) are excluded.
   */
  @Nonnull
  public static List<String> getRoleNames(@Nullable Authentication authentication) {
    if (authentication == null) {
      return List.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a != null && !a.isEmpty())
        .filter(a -> !a.startsWith(AllowedAccountsAuthorities.PREFIX))
        .filter(a -> !a.startsWith(SPINNAKER_PREFIX))
        .filter(a -> !a.startsWith(SCOPE_PREFIX))
        .filter(a -> !PROVIDER_AUTHORITIES.contains(a))
        .filter(a -> !a.equals(ANONYMOUS_AUTHORITY.getAuthority()))
        .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * @return whether the authentication identifies a user. An anonymous token only does when its
   *     principal is a real user (as Gate's anonymous mode provides); the default String principal
   *     ("anonymousUser") does not.
   */
  public static boolean hasIdentity(@Nullable Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication.getName() == null
        || authentication.getName().isBlank()) {
      return false;
    }
    return !(authentication instanceof AnonymousAuthenticationToken)
        || authentication.getPrincipal() instanceof UserDetails;
  }

  @Nonnull
  private static Stream<String> streamRoles(@Nonnull Authentication authentication) {
    return authentication.getAuthorities().stream()
        .filter(SpinnakerAuthorities::isRole)
        .map(SpinnakerAuthorities::getRole);
  }

  private static boolean isRole(@Nonnull GrantedAuthority authority) {
    return authority.getAuthority().startsWith(ROLE_PREFIX);
  }

  private static String getRole(@Nonnull GrantedAuthority authority) {
    return authority.getAuthority().substring(ROLE_PREFIX.length());
  }
}
