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
import com.netflix.spinnaker.security.User;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

/**
 * Explicit response shape for {@code GET /auth/user}, replacing the prior reflection-derived {@code
 * Map} that silently exposed every new {@link User} field. Wire-format keys match the legacy map
 * byte-for-byte so existing consumers keep working.
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
      User user,
      boolean isAdmin,
      boolean canMintApiTokens,
      Integer maxUserTokenLifetimeDays,
      Integer maxServiceAccountTokenLifetimeDays) {
    return new AuthUserResponse(
        user.getEmail(),
        user.getUsername(),
        user.getFirstName(),
        user.getLastName(),
        user.getRoles(),
        user.getAllowedAccounts(),
        user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(Authority::new)
            .collect(Collectors.toList()),
        user.isAccountNonExpired(),
        user.isAccountNonLocked(),
        user.isCredentialsNonExpired(),
        user.isEnabled(),
        isAdmin,
        canMintApiTokens,
        maxUserTokenLifetimeDays,
        maxServiceAccountTokenLifetimeDays);
  }
}
