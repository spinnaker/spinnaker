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

  PermissionsRepository put(UserPermission permission);

  Optional<UserPermission> get(String id);

  /**
   * Gets all UserPermissions in the repository keyed by user ID.
   */
  Map<String, UserPermission> getAllById();

  void remove(String id);
}
