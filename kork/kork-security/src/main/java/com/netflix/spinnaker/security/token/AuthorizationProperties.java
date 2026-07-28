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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The master switch for Spinnaker's authorization system, bound from the top-level {@code authz}
 * prefix and consumed by <em>every</em> service (minters and verifiers alike). It sits above the
 * mechanism-specific sub-namespaces ({@code authz.signing}, {@code authz.verifier}, {@code
 * authz.token}, {@code authz.pdp}, {@code authz.roles}, …), which only carry configuration.
 *
 * <p>{@link #isEnabled()} is modeled on the legacy {@code services.fiat.enabled} flag:
 *
 * <ul>
 *   <li>{@code false} (the default) — authorization is disabled: every {@code hasPermission(...)}
 *       check short-circuits to allow, account/resource policies are allow-all, and an absent or
 *       invalid token falls back to the unsigned legacy identity (for audit only). This matches the
 *       old {@code services.fiat.enabled=false} "allow everything" behavior.
 *   <li>{@code true} — authorization is enforced: a valid signed token is required (absent/invalid
 *       tokens become anonymous), decisions are made locally against the caller's verified token
 *       roles and the resource owner's ACLs, and startup fails fast if signing/verification keys
 *       are not configured.
 * </ul>
 *
 * <p>{@link #isStrict()} ({@code authz.strict}) is a fail-closed switch for the role-only decision
 * points that would otherwise stay permissive (allow) when no cryptographically verified identity
 * token is available. When {@code authz.enabled} <em>and</em> {@code authz.strict} are both {@code
 * true}, those decision points fail closed (deny) instead of allowing on a missing or unverifiable
 * token. It has no effect when {@code authz.enabled} is {@code false}. Defaults to {@code true} so
 * that enabling authorization is fail-closed by default; set it to {@code false} to opt into the
 * permissive rollout posture.
 */
@ConfigurationProperties("authz")
public class AuthorizationProperties {

  /**
   * Master switch for authorization. When {@code false} (default) authorization is disabled
   * (allow-all); when {@code true} authorization is enforced against verified token roles.
   */
  private boolean enabled = false;

  /**
   * Fail-closed switch for role-only decision points. When {@code true} (the default, and while
   * {@link #isEnabled()} is also {@code true}), a decision point that cannot obtain a
   * cryptographically verified identity token fails closed (denies) rather than staying permissive.
   * When {@code false} the permissive rollout behavior is preserved: such decision points allow the
   * request. Ignored while {@link #isEnabled()} is {@code false}.
   */
  private boolean strict = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }
}
