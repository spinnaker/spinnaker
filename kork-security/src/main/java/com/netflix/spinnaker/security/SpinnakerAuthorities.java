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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Constants and utilities for working with Spring Security GrantedAuthority objects specific to
 * Spinnaker and Fiat. Spinnaker-specific roles are represented here as granted authorities with the
 * {@code SPINNAKER_} prefix.
 */
public class SpinnakerAuthorities {
  private static final String ROLE_PREFIX = "ROLE_";

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
