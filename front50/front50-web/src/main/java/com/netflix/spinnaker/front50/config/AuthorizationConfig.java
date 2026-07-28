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
package com.netflix.spinnaker.front50.config;

import java.util.Collections;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authorization configuration for Front50.
 *
 * <p>Controls authorization behavior that does not have a natural home on a specific resource
 * controller. The defaults preserve stock Spinnaker behavior; operators opt into looser checks by
 * setting properties here explicitly.
 */
@Data
@ConfigurationProperties(prefix = "authorization")
public class AuthorizationConfig {

  private RunAsUserRoleCheck runAsUserRoleCheck = new RunAsUserRoleCheck();

  /**
   * Bypass controls for the role-sharing check that is normally performed when saving a pipeline
   * whose trigger declares a {@code runAsUser}.
   *
   * <p>By default, the saver must share at least one role with the trigger's {@code runAsUser}
   * service account. This is the upstream Spinnaker behavior and prevents privilege-escalation via
   * delegation to a more privileged service account. The two fields below allow this check to be
   * bypassed at different scopes — {@link #skipAll} disables it globally, {@link #skipFor} disables
   * it for a specific allowlist of accounts.
   *
   * <p>Neither field affects the complementary sanity check that the service account itself has
   * {@code APPLICATION:EXECUTE} on the target application (so the trigger will actually be able to
   * fire).
   */
  @Data
  public static class RunAsUserRoleCheck {

    /**
     * When {@code true}, the role-sharing check is bypassed for every {@code runAsUser} on every
     * pipeline save. Authorization to save reduces to {@code APPLICATION:WRITE} on the pipeline's
     * application.
     *
     * <p>Enable this only if you treat {@code APPLICATION:WRITE} as a sufficient authorization
     * boundary on its own — e.g. when application permissions, group memberships, and service
     * account memberships are centrally managed and audited outside of Spinnaker, so the
     * privilege-escalation primitive is mitigated at the policy layer instead. For a narrower
     * bypass scoped to specific service accounts, prefer {@link #skipFor}.
     *
     * <p>When {@code true}, {@link #skipFor} is ignored — everyone already bypasses the check.
     */
    private boolean skipAll = false;

    /**
     * Per-account allowlist of service account names that bypass the role-sharing check.
     *
     * <p>Intended for shared, narrowly-scoped <em>trigger</em> service accounts whose only purpose
     * is to allow automated triggers (CRON, webhook, etc.) to fire — for example a single {@code
     * pipeline-runner@spinnaker} account whose {@code memberOf} list contains one role used solely
     * to grant {@code APPLICATION:EXECUTE}.
     *
     * <p>Names are compared case-insensitively. Empty (the default) means no per-account bypass —
     * every {@code runAsUser} must pass the role-sharing check. Ignored when {@link #skipAll} is
     * {@code true}.
     */
    private Set<String> skipFor = Collections.emptySet();
  }
}
