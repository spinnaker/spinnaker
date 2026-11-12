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

package com.netflix.spinnaker.gate.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.security.AllowedAccountsAuthorities;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Explicit response shape for {@code GET /auth/user}, replacing the prior reflection-derived {@code
 * Map} that silently exposed every field of the principal. Wire-format keys match the legacy map so
 * existing consumers keep working. It is derived from the {@link Authentication}, not from a
 * Spinnaker-specific principal type.
 */
public record AuthUserResponse(
    String email,
    String username,
    String firstName,
    String lastName,
    Collection<String> roles,
    Collection<String> allowedAccounts,
    List<Authority> authorities,
    boolean accountNonExpired,
    boolean accountNonLocked,
    boolean credentialsNonExpired,
    boolean enabled,
    boolean isAdmin,
    boolean canMintApiTokens,
    // Lifetime fields are omitted (not nulled) when api-tokens are disabled, matching legacy.
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxUserTokenLifetimeDays,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxServiceAccountTokenLifetimeDays) {

  /**
   * Mirrors {@code SimpleGrantedAuthority}'s default Jackson output of {@code {"authority": …}}.
   */
  public record Authority(String authority) {}

  public static AuthUserResponse from(
      Authentication authentication,
      Collection<String> roles,
      boolean isAdmin,
      boolean canMintApiTokens,
      Integer maxUserTokenLifetimeDays,
      Integer maxServiceAccountTokenLifetimeDays) {
    Object principal = authentication.getPrincipal();
    Map<String, Object> attributes =
        principal instanceof OAuth2User oauth2User ? oauth2User.getAttributes() : Map.of();
    UserDetails details = principal instanceof UserDetails userDetails ? userDetails : null;
    String username = authentication.getName();

    return new AuthUserResponse(
        attribute(attributes, "email", username),
        username,
        attribute(attributes, "given_name", null),
        attribute(attributes, "family_name", null),
        roles,
        AllowedAccountsAuthorities.getAllowedAccountNames(authentication),
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(Authority::new)
            .collect(Collectors.toList()),
        details == null || details.isAccountNonExpired(),
        details == null || details.isAccountNonLocked(),
        details == null || details.isCredentialsNonExpired(),
        details == null || details.isEnabled(),
        isAdmin,
        canMintApiTokens,
        maxUserTokenLifetimeDays,
        maxServiceAccountTokenLifetimeDays);
  }

  private static String attribute(Map<String, Object> attributes, String key, String fallback) {
    Object value = attributes.get(key);
    return value instanceof String string && !string.isBlank() ? string : fallback;
  }
}
