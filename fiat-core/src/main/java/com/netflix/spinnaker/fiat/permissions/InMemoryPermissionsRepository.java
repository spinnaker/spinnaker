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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryPermissionsRepository implements PermissionsRepository {

  private final Map<String, UserPermission> permissions = new HashMap<>();

  @Override
  public InMemoryPermissionsRepository put(UserPermission permission) {
    if (!permission.isPartialPermission()) {
      forcePut(permission);
    }
    return this;
  }

  @Override
  public InMemoryPermissionsRepository forcePut(UserPermission permission) {
    this.permissions.put(permission.getId(), permission);
    return this;
  }

  @Override
  public Optional<UserPermission> get(String id) {
    return Optional.ofNullable(this.permissions.get(id));
  }

  @Override
  public Map<String, UserPermission> getAllById() {
    return new HashMap<>(permissions);
  }

  @Override
  public void remove(String id) {
    this.permissions.remove(id);
  }
}
