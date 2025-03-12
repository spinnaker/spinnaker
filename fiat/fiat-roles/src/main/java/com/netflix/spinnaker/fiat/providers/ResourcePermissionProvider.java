/*
 * Copyright 2019 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import javax.annotation.Nonnull;

/**
 * A ResourcePermissionProvider is responsible for supplying the full set of Permissions for a
 * specific Resource.
 *
 * <p>Note that while the API signature matches ResourcePermissionSource the intent of
 * ResourcePermissionProvider is the interface for consumers interested in the actual Permissions of
 * a resource, while ResourcePermissionSource models a single source of Permissions (for example
 * CloudDriver as a source of Account permissions).
 *
 * @param <T> the type of Resource for which this ResourcePermissionProvider supplies Permissions.
 */
public interface ResourcePermissionProvider<T extends Resource> {

  /**
   * Retrieves Permissions for the supplied resource.
   *
   * @param resource the resource for which to get permissions (never null)
   * @return the Permissions for the resource (never null - use Permissions.EMPTY or apply some
   *     restriction)
   */
  @Nonnull
  Permissions getPermissions(@Nonnull T resource);
}
