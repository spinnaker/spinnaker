/*
 * Copyright 2022 Apple, Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountDefinitionAuthorizer {
  @Nullable private final FiatPermissionEvaluator permissionEvaluator;

  public boolean isAccountManager(@Nonnull String username) {
    if (permissionEvaluator == null) {
      return true;
    }
    // TODO(jvz): update with https://github.com/spinnaker/fiat/pull/928
    //  to check isAccountManager
    var permission = permissionEvaluator.getPermission(username);
    return permission != null && permission.isAdmin();
  }

  public boolean isAdmin(@Nonnull String username) {
    if (permissionEvaluator == null) {
      return true;
    }
    var permission = permissionEvaluator.getPermission(username);
    return permission != null && permission.isAdmin();
  }

  public Set<String> getRoles(@Nonnull String username) {
    if (permissionEvaluator == null) {
      return Set.of();
    }
    var permission = permissionEvaluator.getPermission(username);
    return permission != null
        ? permission.getRoles().stream().map(Role.View::getName).collect(Collectors.toSet())
        : Set.of();
  }

  public boolean canAccessAccount(@Nonnull String username, @Nonnull String accountName) {
    if (permissionEvaluator == null) {
      return true;
    }
    return permissionEvaluator.hasPermission(
        username, accountName, ResourceType.ACCOUNT.getName(), Authorization.WRITE);
  }
}
