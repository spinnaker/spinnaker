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

import java.util.Map;
import java.util.Optional;

/**
 * A PermissionsRepository is responsible for persisting UserPermission objects under a user ID key.
 *
 * TODO(ttomsu): This may be too general, and will need to be optimized for individual resource type
 * reads.
 */
public interface PermissionsRepository {

  /**
   * Adds the specified permission to the repository, overwriting anything under the same id.
   * Will have no effect if the specified permission is partial. See
   * {@link #forcePut(UserPermission)}.
   * @param permission
   * @return This PermissionRepository
   */
  PermissionsRepository put(UserPermission permission);

  /**
   * Adds the specified permission to the repository, even if it is only a partial permission.
   * @param permission
   * @return
   */
  PermissionsRepository forcePut(UserPermission permission);

  /**
   * Gets the UserPermission from the repository, if available. Returns an empty Optional if not
   * found.
   * @param id
   * @return The UserPermission wrapped in an Optional.
   */
  Optional<UserPermission> get(String id);

  /**
   * Gets all UserPermissions in the repository keyed by user ID.
   */
  Map<String, UserPermission> getAllById();

  /**
   * Delete the specified user permission.
   * @param id
   */
  void remove(String id);
}
