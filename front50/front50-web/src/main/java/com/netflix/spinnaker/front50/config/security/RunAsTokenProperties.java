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

package com.netflix.spinnaker.front50.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Front50's run-as token minter. Bound from {@code authz.runas}.
 *
 * <p>The signing key material itself is <em>not</em> configured here — it comes from the shared
 * {@code authz.signing} key set ({@link
 * com.netflix.spinnaker.security.token.IdentityTokenSigningProperties}), the same key(s) Gate signs
 * with. This class only configures run-as-specific minter behaviour.
 *
 * <p>Callers of the initial service-account mint authenticate without any shared secret and without
 * holding a signing key: either by presenting a signature-valid Spinnaker identity token (Orca) or
 * by an authenticated service-to-service caller identity (Echo/Orca via {@code authz.s2s}: mTLS /
 * mesh / Kubernetes ServiceAccount token). The execution-token re-issue endpoint is likewise
 * authorized by the Orca s2s caller identity, so Echo and Orca hold no identity-token signing key —
 * only Gate and Front50 do.
 */
@ConfigurationProperties("authz.runas")
public class RunAsTokenProperties {

  /** Whether the run-as mint endpoint is enabled. */
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
