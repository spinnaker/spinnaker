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

package com.netflix.spinnaker.clouddriver.config.security;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import javax.annotation.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Clouddriver's owner-local Policy Enforcement Point. Extends the kork {@link
 * PolicyDecisionPointPermissionEvaluator} (the Spring {@code PermissionEvaluator} that {@code
 * hasPermission(...)} binds to) and adds the helper methods Clouddriver's {@code @PreAuthorize}
 * SpEL expressions reference on the {@code @spinnakerPermissionEvaluator} bean.
 *
 * <p>The bean is registered under the name {@code spinnakerPermissionEvaluator} so the annotations
 * resolve against that SpEL reference; decisions are made locally against the caller's verified
 * token roles and the embedded ACLs the wired {@link ResourceAclResolver} resolves — {@code
 * account} ACLs from Clouddriver's own credentials, and {@code application} ACLs read from their
 * owner (Front50). When an application ACL still cannot be resolved (Front50 unavailable, or an
 * application with no permission record), the {@code allowAccessToUnknownApplications} fallback
 * applies, exactly as it did under Fiat.
 */
public class ClouddriverPermissionEvaluator extends PolicyDecisionPointPermissionEvaluator {

  public ClouddriverPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      @Nullable ResourceAclResolver resourceAclResolver,
      boolean enabled,
      boolean allowAccessToUnknownApplications) {
    super(policyDecisionPoint, resourceAclResolver, enabled, allowAccessToUnknownApplications);
  }

  /** True when the current caller carries the Spinnaker admin authority (from the token). */
  public boolean isAdmin() {
    return SpinnakerAuthorities.isAdmin(SecurityContextHolder.getContext().getAuthentication());
  }
}
