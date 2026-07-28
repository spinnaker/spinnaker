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

package com.netflix.spinnaker.security.authz;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * The Policy Enforcement Point: a Spring Security {@link PermissionEvaluator} that the
 * {@code @PreAuthorize("hasPermission(...)")}/{@code @PostFilter} annotations bind to. It is the
 * stable call-site seam — the underlying {@link PolicyDecisionPoint} (Spring ACL adapter by
 * default, the legacy-permissions fallback, or a future OPA/OpenFGA adapter) can be swapped without
 * editing annotations.
 *
 * <p>It applies Spinnaker's role-based global short-circuits — admin bypasses all ACL consultation,
 * and account-manager bypasses {@code account} ACL consultation — then delegates the per-resource
 * decision to the configured PDP using roles read from the (token-derived) authorities and the
 * resource's embedded ACL.
 */
public class PolicyDecisionPointPermissionEvaluator implements PermissionEvaluator {

  private final PolicyDecisionPoint policyDecisionPoint;
  @Nullable private final ResourceAclResolver resourceAclResolver;
  private final boolean enabled;
  private final boolean allowAccessToUnknownApplications;

  public PolicyDecisionPointPermissionEvaluator(PolicyDecisionPoint policyDecisionPoint) {
    this(policyDecisionPoint, null, true, false);
  }

  public PolicyDecisionPointPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      @Nullable ResourceAclResolver resourceAclResolver,
      boolean enabled,
      boolean allowAccessToUnknownApplications) {
    this.policyDecisionPoint = policyDecisionPoint;
    this.resourceAclResolver = resourceAclResolver;
    this.enabled = enabled;
    this.allowAccessToUnknownApplications = allowAccessToUnknownApplications;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Object targetDomainObject, Object permission) {
    if (!enabled) {
      return true;
    }
    if (authentication == null || targetDomainObject == null) {
      return false;
    }
    if (SpinnakerAuthorities.isAdmin(authentication)) {
      return true;
    }
    if (targetDomainObject instanceof ProtectedResource) {
      ProtectedResource resource = (ProtectedResource) targetDomainObject;
      return decide(
          authentication,
          resource.getResourceType(),
          resource.getName(),
          permission,
          resource.getPermissions());
    }
    // Domain objects that authorize themselves (the kork-security AccessControlled contract).
    if (targetDomainObject instanceof com.netflix.spinnaker.security.AccessControlled) {
      return ((com.netflix.spinnaker.security.AccessControlled) targetDomainObject)
          .isAuthorized(authentication, permission);
    }
    return false;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Serializable targetId, String targetType, Object permission) {
    if (!enabled) {
      return true;
    }
    if (authentication == null || targetId == null || targetType == null) {
      return false;
    }
    if (SpinnakerAuthorities.isAdmin(authentication)) {
      return true;
    }
    ResourceType resourceType = ResourceType.parse(targetType);
    Permissions acl =
        resourceAclResolver == null
            ? null
            : resourceAclResolver.resolve(resourceType, targetId.toString());
    if (acl == null) {
      // The resource ACL could not be resolved (unknown/absent resource, or the owner-local
      // resolver is not yet wired). Preserve the legacy allowAccessToUnknownApplications behavior.
      return ResourceType.APPLICATION.equals(resourceType) && allowAccessToUnknownApplications;
    }
    return decide(authentication, resourceType, targetId.toString(), permission, acl);
  }

  private boolean decide(
      Authentication authentication,
      ResourceType resourceType,
      String resourceName,
      Object permission,
      Permissions acl) {
    if (ResourceType.ACCOUNT.equals(resourceType)
        && SpinnakerAuthorities.isAccountManager(authentication)) {
      return true;
    }
    Authorization action = Authorization.parse(permission);
    return policyDecisionPoint.decide(
        roleNames(authentication), resourceType, resourceName, action, acl);
  }

  private static List<String> roleNames(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .collect(Collectors.toList());
  }
}
