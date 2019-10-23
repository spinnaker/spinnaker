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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

public final class Front50ApplicationResourcePermissionSource
    implements ResourcePermissionSource<Application> {

  private final Authorization executeFallback;

  public Front50ApplicationResourcePermissionSource(Authorization executeFallback) {
    this.executeFallback = executeFallback;
  }

  @Override
  @Nonnull
  public Permissions getPermissions(@Nonnull Application resource) {
    Permissions storedPermissions = resource.getPermissions();
    if (storedPermissions == null || !storedPermissions.isRestricted()) {
      return Permissions.EMPTY;
    }

    Map<Authorization, List<String>> authorizations =
        Arrays.stream(Authorization.values()).collect(toMap(identity(), storedPermissions::get));

    // If the execute permission wasn't set, copy the permissions from whatever is specified in the
    // config's executeFallback flag
    if (authorizations.get(Authorization.EXECUTE).isEmpty()) {
      authorizations.put(Authorization.EXECUTE, authorizations.get(executeFallback));
    }

    // CREATE permissions are not allowed on the resource level.
    authorizations.remove(Authorization.CREATE);

    return Permissions.Builder.factory(authorizations).build();
  }
}
