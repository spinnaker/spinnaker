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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gate-specific authorization settings (bound from {@code authz.gate}) that complement the shared
 * {@code authz.token} ({@link com.netflix.spinnaker.security.token.SpinnakerTokenSettings}) and
 * {@code authz.roles} ({@link
 * com.netflix.spinnaker.security.roles.config.RoleResolutionProperties}) settings.
 *
 * <p>These configure how Gate, acting as the edge minter, resolves a caller's roles at login and
 * mints the signed identity token that downstream services verify.
 */
@ConfigurationProperties("authz.gate")
public class GateAuthzProperties {

  /**
   * Short-TTL per-principal role cache window. The cache is consulted when re-minting the
   * (short-lived) identity token on each downstream request and on the high-volume API-token path,
   * so the role providers are not hit on every request. Uses expire-after-access so actively used
   * sessions keep their resolved (and externally-asserted) roles warm.
   */
  private Duration roleCacheTtl = Duration.ofMinutes(10);

  /** Maximum number of principals held in the role cache. */
  private long roleCacheMaximumSize = 10_000;

  /**
   * Role names that grant Spinnaker administrator status. When a caller holds any of these roles
   * the minted token carries the {@code admin} claim. Empty by default (no role-based admin).
   */
  private List<String> adminRoles = new ArrayList<>();

  /**
   * Role names that grant Spinnaker account-manager status. When a caller holds any of these roles
   * the minted token carries the {@code account_manager} claim.
   */
  private List<String> accountManagerRoles = new ArrayList<>();

  public Duration getRoleCacheTtl() {
    return roleCacheTtl;
  }

  public void setRoleCacheTtl(Duration roleCacheTtl) {
    this.roleCacheTtl = roleCacheTtl;
  }

  public long getRoleCacheMaximumSize() {
    return roleCacheMaximumSize;
  }

  public void setRoleCacheMaximumSize(long roleCacheMaximumSize) {
    this.roleCacheMaximumSize = roleCacheMaximumSize;
  }

  public List<String> getAdminRoles() {
    return adminRoles;
  }

  public void setAdminRoles(List<String> adminRoles) {
    this.adminRoles = adminRoles == null ? new ArrayList<>() : adminRoles;
  }

  public List<String> getAccountManagerRoles() {
    return accountManagerRoles;
  }

  public void setAccountManagerRoles(List<String> accountManagerRoles) {
    this.accountManagerRoles =
        accountManagerRoles == null ? new ArrayList<>() : accountManagerRoles;
  }
}
