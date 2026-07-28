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

package com.netflix.spinnaker.security.authz.pdp.acl;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.security.acls.AclPermissionEvaluator;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Default {@link PolicyDecisionPoint}: the adapter-based Spring Security ACL evaluator. It runs the
 * decision through {@link AclPermissionEvaluator} over an {@link EmbeddedPermissionsAclService}
 * that synthesizes an {@code Acl} from the resource's embedded {@link Permissions} (no ACL data
 * migration). Spinnaker's role-based admin / account-manager short-circuits and the {@code
 * allowAccessToUnknownApplications} behavior are applied by the enclosing {@link
 * com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator}.
 */
public class SpringAclPolicyDecisionPoint implements PolicyDecisionPoint {

  public static final String PROVIDER_ID = "spring-acl";

  private final SpinnakerPermissionFactory permissionFactory = new SpinnakerPermissionFactory();

  public SpringAclPolicyDecisionPoint() {}

  @Override
  public boolean decide(
      @Nonnull Collection<String> roles,
      @Nonnull ResourceType resourceType,
      @Nonnull String resourceName,
      @Nullable Authorization action,
      @Nonnull Permissions resourceAcl) {

    // Service accounts have no read/write authorizations and Spring ACL has no concept of "assume":
    // a caller may assume the SA when it is unrestricted or shares one of its roles.
    if (ResourceType.SERVICE_ACCOUNT.equals(resourceType)) {
      return !resourceAcl.isRestricted() || intersects(roles, resourceAcl.allGroups());
    }

    // An unrestricted resource grants every authorization to everyone (mirrors
    // Permissions.getAuthorizations returning Authorization.ALL when not restricted).
    if (!resourceAcl.isRestricted()) {
      return true;
    }

    if (action == null) {
      return intersects(roles, resourceAcl.allGroups());
    }

    ObjectIdentity objectIdentity = new ObjectIdentityImpl(resourceType.getName(), resourceName);
    EmbeddedPermissionsAclService aclService =
        new EmbeddedPermissionsAclService(oid -> resourceAcl);
    AclPermissionEvaluator evaluator = new AclPermissionEvaluator(aclService);
    evaluator.setPermissionFactory(permissionFactory);

    Authentication authentication = authenticationFor(roles);
    return evaluator.hasPermission(
        authentication, resourceName, resourceType.getName(), action.name());
  }

  @Nonnull
  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  private static Authentication authenticationFor(Collection<String> roles) {
    List<GrantedAuthority> authorities =
        roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());
    return new PreAuthenticatedAuthenticationToken("authz-pdp", "N/A", authorities);
  }

  private static boolean intersects(Collection<String> roles, Set<String> groups) {
    Set<String> normalized =
        roles.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    return !Collections.disjoint(normalized, groups);
  }
}
