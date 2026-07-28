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

package com.netflix.spinnaker.igor.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Igor-local authorization settings (bound from {@code authz.igor}).
 *
 * <p>Igor is a <em>verifier-only</em> participant in the token-carried identity model: unlike Gate
 * and Front50 it never mints tokens (it holds no signing key), it only verifies inbound identity
 * tokens against the trusted minters' JWKS endpoints. Those endpoints are derived from {@code
 * services.gate.baseUrl} / {@code services.front50.baseUrl} (see {@link
 * com.netflix.spinnaker.security.token.IdentityTokenVerifierProperties}); this class retains only
 * Igor-specific policy knobs.
 */
@ConfigurationProperties("authz.igor")
public class IgorAuthzProperties {

  /**
   * When {@code true} (the default), a {@code build_service} that Igor does not own/know is treated
   * permissively (the request reaches the controller, which returns its own not-found response)
   * rather than being denied at the policy layer. This preserves Igor's historical behavior:
   * owner-local enforcement can only restrict build services Igor actually holds an ACL for.
   */
  private boolean allowAccessToUnknownBuildServices = true;

  public boolean isAllowAccessToUnknownBuildServices() {
    return allowAccessToUnknownBuildServices;
  }

  public void setAllowAccessToUnknownBuildServices(boolean allowAccessToUnknownBuildServices) {
    this.allowAccessToUnknownBuildServices = allowAccessToUnknownBuildServices;
  }
}
