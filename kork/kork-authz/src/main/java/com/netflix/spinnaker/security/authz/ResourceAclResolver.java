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

import javax.annotation.Nullable;

/**
 * Resolves a resource's embedded ACL by type and name for the {@code hasPermission(authentication,
 * targetId, targetType, permission)} call path, where only identifiers are available. Owning
 * services wire this against their own in-process domain model (e.g. Front50 applications,
 * Clouddriver accounts) in a later phase; until then the by-id call path has no ACL to consult.
 */
@FunctionalInterface
public interface ResourceAclResolver {

  /**
   * @return the resource's embedded ACL, or {@code null} if it cannot be resolved by this service
   */
  @Nullable
  Permissions resolve(ResourceType resourceType, String resourceName);
}
