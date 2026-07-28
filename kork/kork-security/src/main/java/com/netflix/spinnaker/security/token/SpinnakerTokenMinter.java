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

package com.netflix.spinnaker.security.token;

import java.util.List;
import javax.annotation.Nonnull;

/**
 * Mints signed Spinnaker identity tokens.
 *
 * <p>Only trusted minters (Gate after authentication, and the dedicated run-as minter) hold a
 * signing key; downstream services only ever {@link SpinnakerTokenVerifier verify} tokens.
 */
public interface SpinnakerTokenMinter {

  /**
   * Mint a signed, compact-serialized identity token for the supplied claims. The issuer, audience,
   * issued-at and expiry are populated by the minter from its configured settings.
   *
   * @param claims the identity claims to embed (subject, roles, admin/account-manager flags)
   * @return the compact-serialized signed JWT
   */
  @Nonnull
  String mint(@Nonnull SpinnakerTokenClaims claims);

  /** Convenience overload that builds the claims from the supplied identity attributes. */
  @Nonnull
  default String mint(
      @Nonnull String subject, @Nonnull List<String> roles, boolean admin, boolean accountManager) {
    return mint(
        SpinnakerTokenClaims.builder(subject)
            .roles(roles)
            .admin(admin)
            .accountManager(accountManager)
            .build());
  }
}
