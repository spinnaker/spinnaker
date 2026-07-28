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

package com.netflix.spinnaker.security.authz.pdp;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Behavior-preserving port of the legacy permission-evaluation logic reduced to the owner-local
 * decision: it evaluates a caller's roles against a single resource's embedded {@link Permissions}
 * ACL. This is the config-selectable fallback {@link PolicyDecisionPoint}, kept as a comparison
 * point if the Spring ACL adapter (the default) ever diverges.
 *
 * <p>It intentionally drops the old remote permission-service lookup, the materialized
 * per-user-permission cache and the legacy-fallback path that depended on the now-removed
 * materialized permission store. The admin / account-manager short-circuits and the {@code
 * allowAccessToUnknownApplications} behavior are applied by the enclosing {@link
 * com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator}.
 */
public class LegacyPermissionsPolicyDecisionPoint implements PolicyDecisionPoint {

  public static final String PROVIDER_ID = "legacy";

  public LegacyPermissionsPolicyDecisionPoint() {}

  @Override
  public boolean decide(
      @Nonnull Collection<String> roles,
      @Nonnull ResourceType resourceType,
      @Nonnull String resourceName,
      @Nullable Authorization action,
      @Nonnull Permissions resourceAcl) {

    // Service accounts have no read/write authorizations: a caller may assume the SA if it is
    // unrestricted or the caller holds one of the SA's roles.
    if (ResourceType.SERVICE_ACCOUNT.equals(resourceType)) {
      return !resourceAcl.isRestricted() || intersects(roles, resourceAcl.allGroups());
    }

    if (action == null) {
      // No specific authorization requested: granted if unrestricted or the caller has any grant.
      return !resourceAcl.isRestricted()
          || !resourceAcl.getAuthorizations(roleList(roles)).isEmpty();
    }

    // An unrestricted resource yields Authorization.ALL, so open resources are granted here too.
    return resourceAcl.getAuthorizations(roleList(roles)).contains(action);
  }

  @Nonnull
  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  private static java.util.List<String> roleList(Collection<String> roles) {
    return new ArrayList<>(roles);
  }

  private static boolean intersects(Collection<String> roles, Set<String> groups) {
    Set<String> normalized =
        roles.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    return !Collections.disjoint(normalized, groups);
  }
}
