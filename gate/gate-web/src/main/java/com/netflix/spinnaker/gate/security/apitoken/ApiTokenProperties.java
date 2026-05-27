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

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Spinnaker API token subsystem.
 *
 * <pre>
 * api-tokens:
 *   enabled: false
 *   token-prefix: spk_
 *   redis-key-prefix: api-token
 *   reject-check-interval-seconds: 60
 *   max-user-token-lifetime-days: 90
 *   max-service-account-token-lifetime-days: 365
 *   allowed-minting-roles:
 *     - spinnaker-api-users
 * </pre>
 */
@Data
@ConfigurationProperties("api-tokens")
public class ApiTokenProperties {

  /** Master switch — no beans are registered unless this is {@code true}. */
  private boolean enabled = false;

  /** Expected prefix for all API token values (used to distinguish from OAuth2 Bearer tokens). */
  private String tokenPrefix = "spk_";

  /**
   * Random bytes used for the token's hex suffix at mint time. 30 bytes = 240 bits of entropy. Only
   * affects newly minted tokens.
   */
  private int tokenRandomBytes = 30;

  /**
   * Redis key namespace prefix (so Gate can share a Redis instance with other services without
   * collisions).
   */
  private String redisKeyPrefix = "api-token";

  /**
   * How often (seconds) Gate re-checks a token's principal against Fiat when {@code
   * reject-if-no-principal-permissions} is enabled. Last-check timestamp is stored on the token so
   * all Gate replicas share the throttle.
   */
  private int rejectCheckIntervalSeconds = 60;

  /** Maximum lifetime for user-owned tokens. Enforced on create; tokens cannot be extended. */
  private int maxUserTokenLifetimeDays = 90;

  /** Maximum lifetime for service-account tokens (typically longer than user tokens). */
  private int maxServiceAccountTokenLifetimeDays = 365;

  /** Fiat roles permitted to mint tokens. Admins always bypass this check. */
  private List<String> allowedMintingRoles = new ArrayList<>();

  /**
   * When {@code true} (and Fiat is enabled), reject tokens whose principal has been deprovisioned
   * (no Fiat roles). Throttled by {@link #rejectCheckIntervalSeconds}.
   */
  private boolean rejectIfNoPrincipalPermissions = false;
}
