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

package com.netflix.spinnaker.gate.security;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ProtectedResource;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Owner-local {@link ProtectedResource} view over an application entry that Gate received from
 * Front50 (its owner) and caches as a plain {@code Map}.
 *
 * <p>Gate is the edge aggregator, not the owner of applications, so it has no application ACL store
 * and no {@code ResourceAclResolver}. Instead, Front50 — the owner — embeds each application's ACL
 * in the {@code permissions} attribute of every application it returns (see Front50's {@code
 * v2/applications} controller). This adapter exposes that owner-provided ACL through the {@link
 * ProtectedResource} contract so Gate's {@code @PostFilter} can authorize the cached list via the
 * embedded-ACL overload of {@link
 * com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator#hasPermission(
 * org.springframework.security.core.Authentication, Object, Object)} — a purely local decision
 * driven by the owner's data. An application without an embedded ACL is unrestricted ({@link
 * Permissions#EMPTY}), which the PDP treats as readable by everyone.
 */
public final class GateApplicationResource implements ProtectedResource {

  private final String name;
  private final Permissions permissions;

  private GateApplicationResource(@Nullable String name, Permissions permissions) {
    this.name = name;
    this.permissions = permissions;
  }

  /**
   * Wrap an application attribute map (as cached/returned by Gate) as a {@link ProtectedResource}.
   */
  public static GateApplicationResource from(@Nullable Map<String, ?> application) {
    if (application == null) {
      return new GateApplicationResource(null, Permissions.EMPTY);
    }
    Object name = application.get("name");
    return new GateApplicationResource(
        name == null ? null : name.toString(), parsePermissions(application.get("permissions")));
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ResourceType getResourceType() {
    return ResourceType.APPLICATION;
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  /**
   * Build a {@link Permissions} ACL from the owner-provided {@code permissions} attribute. Front50
   * serializes the ACL as a map of {@code authorization -> [roles]} (e.g. {@code {"READ":["team"],
   * "WRITE":["team"]}}); an already-materialized {@link Permissions} is passed through. Anything
   * else (absent/unrestricted) yields {@link Permissions#EMPTY}.
   */
  private static Permissions parsePermissions(@Nullable Object raw) {
    if (raw instanceof Permissions) {
      return (Permissions) raw;
    }
    if (!(raw instanceof Map)) {
      return Permissions.EMPTY;
    }
    Permissions.Builder builder = new Permissions.Builder();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
      Authorization authorization = parseAuthorization(entry.getKey());
      if (authorization == null || !(entry.getValue() instanceof Collection)) {
        continue;
      }
      for (Object role : (Collection<?>) entry.getValue()) {
        if (role != null) {
          builder.add(authorization, role.toString());
        }
      }
    }
    return builder.build();
  }

  @Nullable
  private static Authorization parseAuthorization(@Nullable Object key) {
    if (key == null) {
      return null;
    }
    try {
      return Authorization.parse(key);
    } catch (IllegalArgumentException e) {
      // Unknown authorization key in the owner-provided ACL; ignore it rather than failing the
      // list.
      return null;
    }
  }
}
