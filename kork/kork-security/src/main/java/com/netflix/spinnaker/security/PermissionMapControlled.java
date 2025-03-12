/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.security;

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;

/**
 * Common interface for access-controlled classes which use a permission map.
 *
 * @param <Authorization> Authorization enum type
 */
@Alpha
public interface PermissionMapControlled<Authorization extends Enum<Authorization>>
    extends AccessControlled {
  @Nullable
  Authorization valueOf(@Nullable Object authorization);

  @Nonnull
  default Map<Authorization, Set<String>> getPermissions() {
    return Map.of();
  }

  @Override
  default boolean isAuthorized(Authentication authentication, Object authorization) {
    Authorization auth = valueOf(authorization);
    if (auth == null) {
      return false;
    }
    Set<String> permittedRoles = getPermissions().getOrDefault(auth, Set.of());
    return permittedRoles.isEmpty()
        || SpinnakerAuthorities.hasAnyRole(authentication, permittedRoles);
  }
}
