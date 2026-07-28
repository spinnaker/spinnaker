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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A resource an owning service can authorize against using its own in-process ACL. Implemented by
 * domain objects (e.g. an application or account) that carry an embedded {@link Permissions} ACL,
 * so the {@link PolicyDecisionPointPermissionEvaluator} can derive the resource type, name and ACL
 * directly from the object passed to a {@code hasPermission} check.
 */
public interface ProtectedResource {
  String getName();

  @JsonIgnore
  ResourceType getResourceType();

  /** The resource's embedded ACL; may be {@link Permissions#EMPTY} for an unrestricted resource. */
  @JsonIgnore
  Permissions getPermissions();
}
