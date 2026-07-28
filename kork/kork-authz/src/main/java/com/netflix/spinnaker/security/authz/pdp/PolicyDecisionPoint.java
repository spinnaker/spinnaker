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
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Service Provider Interface for the authorization decision: given a caller's roles and a
 * resource's embedded ACL, decide whether the requested action is allowed.
 *
 * <p>This is the stable decision seam behind the Spring {@code PermissionEvaluator} used by
 * {@code @PreAuthorize}/{@code @PostFilter}. The default implementation is an adapter over Spring
 * Security's ACL {@code AclPermissionEvaluator}; a behavior-preserving legacy-permissions
 * implementation is available as a config-selectable fallback. Implementations for
 * OPA/OpenFGA/Cerbos can be added behind this same interface without touching call sites.
 *
 * <p>Role-based global short-circuits (admin / account-manager) are applied by the enclosing {@link
 * com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator} before the PDP is
 * consulted, so implementations only need to reason about the resource ACL.
 */
public interface PolicyDecisionPoint {

  /**
   * Decide whether a caller with the supplied {@code roles} may perform {@code action} on the named
   * resource, given the resource's embedded ACL.
   *
   * @param roles the caller's resolved role names (typically from the verified identity token)
   * @param resourceType the type of resource being accessed
   * @param resourceName the name/id of the resource being accessed
   * @param action the requested authorization (may be {@code null} for service-account checks)
   * @param resourceAcl the resource's embedded ACL; {@link Permissions#EMPTY} when unrestricted
   * @return {@code true} if access is granted
   */
  boolean decide(
      @Nonnull Collection<String> roles,
      @Nonnull ResourceType resourceType,
      @Nonnull String resourceName,
      @Nullable Authorization action,
      @Nonnull Permissions resourceAcl);

  /** Stable identifier used for {@code authz.pdp.provider} selection and logging. */
  @Nonnull
  String getProviderId();
}
