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

package com.netflix.spinnaker.security.authz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@link com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint}, bound
 * from the {@code authz.pdp} property prefix.
 */
@ConfigurationProperties("authz.pdp")
public class AuthzPolicyProperties {

  /** Selectable PDP backend: {@code spring-acl} (default) or {@code legacy} (behavior fallback). */
  private String provider = "spring-acl";

  /**
   * Preserves the legacy {@code allowAccessToUnknownApplications} behavior for applications not yet
   * backed by an ACL.
   */
  private boolean allowAccessToUnknownApplications = false;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public boolean isAllowAccessToUnknownApplications() {
    return allowAccessToUnknownApplications;
  }

  public void setAllowAccessToUnknownApplications(boolean allowAccessToUnknownApplications) {
    this.allowAccessToUnknownApplications = allowAccessToUnknownApplications;
  }
}
