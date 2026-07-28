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

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Front50's owner-local Policy Enforcement Point. Extends the kork {@link
 * PolicyDecisionPointPermissionEvaluator} (the Spring {@code PermissionEvaluator} that {@code
 * hasPermission(...)} binds to) and adds the few helper methods that Front50's
 * {@code @PreAuthorize} SpEL expressions reference on the {@code @spinnakerPermissionEvaluator}
 * bean — {@code isAdmin()} and {@code canCreate(...)}.
 *
 * <p>The bean is registered under the name {@code spinnakerPermissionEvaluator} so the annotations
 * resolve against that SpEL bean reference; the decision is made locally against the caller's
 * verified token roles and Front50's own resource ACLs (resolved by the {@link
 * ResourceAclResolver}).
 */
public class Front50PermissionEvaluator extends PolicyDecisionPointPermissionEvaluator {

  private final ApplicationDefaultPermissionsProperties applicationDefaultPermissions;

  public Front50PermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      @Nullable ResourceAclResolver resourceAclResolver,
      boolean enabled,
      boolean allowAccessToUnknownApplications,
      ApplicationDefaultPermissionsProperties applicationDefaultPermissions) {
    super(policyDecisionPoint, resourceAclResolver, enabled, allowAccessToUnknownApplications);
    this.applicationDefaultPermissions = applicationDefaultPermissions;
  }

  /** True when the current caller carries the Spinnaker admin authority (from the token). */
  public boolean isAdmin() {
    return SpinnakerAuthorities.isAdmin(SecurityContextHolder.getContext().getAuthentication());
  }

  /**
   * Whether the current caller may create the supplied resource. Admins always may. For
   * applications, creation is governed by the global {@code authz.application.default-permissions}
   * {@code CREATE} roles (the owner-local equivalent of the legacy {@code CREATE} prefix grant): an
   * application has no resource-level ACL before it exists, so the global {@code CREATE} role set
   * is the authority for who may create one. When no {@code CREATE} roles are configured the check
   * stays permissive (any authenticated caller), preserving the rollout posture; once configured,
   * only callers carrying one of those roles (plus admins) may create.
   */
  public boolean canCreate(String resourceType, Object resource) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }
    if (SpinnakerAuthorities.isAdmin(auth)) {
      return true;
    }
    if (!ResourceType.APPLICATION.equals(ResourceType.parse(resourceType))) {
      // Only application creation is governed by the global defaults; other resources stay
      // permissive for any authenticated caller.
      return true;
    }
    Set<String> createRoles = applicationDefaultPermissions.getCreateRoles();
    if (createRoles.isEmpty()) {
      // Inert when unconfigured: keep application creation permissive during rollout.
      return true;
    }
    return SpinnakerAuthorities.getRoles(auth).stream()
        .map(role -> role.toLowerCase(Locale.ROOT))
        .anyMatch(createRoles::contains);
  }
}
