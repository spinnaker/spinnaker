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

import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import java.io.Serializable;
import org.springframework.security.core.Authentication;

/**
 * Igor's owner-local Policy Enforcement Point. Extends the kork {@link
 * PolicyDecisionPointPermissionEvaluator} (the Spring {@code PermissionEvaluator} that {@code
 * hasPermission(...)} binds to) so the {@code @PreAuthorize}/{@code @PostFilter} annotations on
 * Igor's controllers decide locally — against the caller's verified token roles and Igor's own
 * build-service ACLs.
 *
 * <p>The bean is registered under the name {@code spinnakerPermissionEvaluator} so any {@code
 * @spinnakerPermissionEvaluator}/{@code hasPermission} SpEL resolves against it.
 *
 * <p>Igor only authorizes {@code build_service} resources. For a build service Igor does not own an
 * ACL for, the by-id resolver yields {@code null} and the base evaluator would deny; when {@code
 * allowAccessToUnknownBuildServices} is set this evaluator instead returns {@code true} so the
 * request reaches the controller (which surfaces its own not-found response), preserving Igor's
 * historical behavior.
 */
public class IgorPermissionEvaluator extends PolicyDecisionPointPermissionEvaluator {

  private final ResourceAclResolver resourceAclResolver;
  private final boolean allowAccessToUnknownBuildServices;

  public IgorPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      ResourceAclResolver resourceAclResolver,
      boolean allowAccessToUnknownBuildServices) {
    // Igor has no notion of the `application` resource, so allowAccessToUnknownApplications is off.
    super(policyDecisionPoint, resourceAclResolver, true, false);
    this.resourceAclResolver = resourceAclResolver;
    this.allowAccessToUnknownBuildServices = allowAccessToUnknownBuildServices;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Serializable targetId, String targetType, Object permission) {
    if (super.hasPermission(authentication, targetId, targetType, permission)) {
      return true;
    }
    if (allowAccessToUnknownBuildServices
        && authentication != null
        && targetId != null
        && targetType != null) {
      ResourceType resourceType = ResourceType.parse(targetType);
      if (ResourceType.BUILD_SERVICE.equals(resourceType)
          && resourceAclResolver.resolve(resourceType, targetId.toString()) == null) {
        return true;
      }
    }
    return false;
  }
}
