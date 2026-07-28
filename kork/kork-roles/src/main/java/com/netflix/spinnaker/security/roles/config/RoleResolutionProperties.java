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

package com.netflix.spinnaker.security.roles.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for {@code kork-roles} role resolution, bound from {@code authz.roles}. */
@ConfigurationProperties("authz.roles")
public class RoleResolutionProperties {

  /**
   * Whether to merge each user's externally-asserted roles (e.g. from a SAML assertion) with the
   * roles resolved by the configured provider. Defaults to {@code true}.
   */
  private boolean mergeExternalRoles = true;

  public boolean isMergeExternalRoles() {
    return mergeExternalRoles;
  }

  public void setMergeExternalRoles(boolean mergeExternalRoles) {
    this.mergeExternalRoles = mergeExternalRoles;
  }
}
