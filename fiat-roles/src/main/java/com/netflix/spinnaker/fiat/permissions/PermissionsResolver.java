/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.permissions;

import com.netflix.spinnaker.fiat.model.UserPermission;
import java.util.Collection;
import java.util.Map;

public interface PermissionsResolver {

  /** @return The UserPermission for an anonymous user. */
  UserPermission resolveUnrestrictedUser() throws PermissionResolutionException;

  /** Resolves a single user's permissions. */
  UserPermission resolve(String userId) throws PermissionResolutionException;

  /**
   * Resolves a single user's permissions, taking into account externally provided list of roles.
   */
  UserPermission resolveAndMerge(ExternalUser user) throws PermissionResolutionException;

  /** Resolves multiple user's permissions. Returned map is keyed by userId. */
  Map<String, UserPermission> resolve(Collection<ExternalUser> users)
      throws PermissionResolutionException;
}
