/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * AggregatingResourcePermissionProvider additively combines permissions from all
 * ResourcePermissionSources to build a resulting Permissions object.
 *
 * @param <T> the type of Resource for this AggregatingResourcePermissionProvider
 */
public class AggregatingResourcePermissionProvider<T extends Resource>
    implements ResourcePermissionProvider<T> {

  private final List<ResourcePermissionSource<T>> resourcePermissionSources;

  public AggregatingResourcePermissionProvider(
      List<ResourcePermissionSource<T>> resourcePermissionSources) {
    this.resourcePermissionSources = resourcePermissionSources;
  }

  @Override
  @Nonnull
  public Permissions getPermissions(@Nonnull T resource) {
    Permissions.Builder builder = new Permissions.Builder();
    for (ResourcePermissionSource<T> source : resourcePermissionSources) {
      Permissions permissions = source.getPermissions(resource);
      if (permissions.isRestricted()) {
        for (Authorization auth : Authorization.values()) {
          builder.add(auth, permissions.get(auth));
        }
      }
    }

    return builder.build();
  }
}
