/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@NonnullByDefault
public class DefaultAccountSecurityPolicy implements AccountSecurityPolicy {
  private final FiatPermissionEvaluator permissionEvaluator;

  @Override
  public boolean isAdmin(String username) {
    return Optional.ofNullable(permissionEvaluator.getPermission(username))
        .filter(UserPermission.View::isAdmin)
        .isPresent();
  }

  @Override
  public boolean isAccountManager(String username) {
    return Optional.ofNullable(permissionEvaluator.getPermission(username))
        .filter(permission -> isAccountManager(permission) || permission.isAdmin())
        .isPresent();
  }

  @Override
  public Set<String> getRoles(String username) {
    return Optional.ofNullable(permissionEvaluator.getPermission(username)).stream()
        .flatMap(permission -> permission.getRoles().stream().map(Role.View::getName))
        .collect(Collectors.toSet());
  }

  @Override
  public boolean canUseAccount(@Nonnull String username, @Nonnull String account) {
    // note that WRITE permissions are required in order to do anything with an account as the READ
    // permission
    // is only used for certain UI items related to the account
    return Optional.ofNullable(permissionEvaluator.getPermission(username))
        .filter(
            permission ->
                permission.isAdmin()
                    || permissionEvaluator.hasPermission(
                        username, account, ResourceType.ACCOUNT.getName(), Authorization.WRITE))
        .isPresent();
  }

  @Override
  public boolean canModifyAccount(@Nonnull String username, @Nonnull String account) {
    // note that WRITE permissions are required in order to do anything with an account as the READ
    // permission
    // is only used for certain UI items related to the account
    return Optional.ofNullable(permissionEvaluator.getPermission(username))
        .filter(
            permission ->
                permission.isAdmin()
                    || isAccountManager(permission)
                        && permissionEvaluator.hasPermission(
                            username, account, ResourceType.ACCOUNT.getName(), Authorization.WRITE))
        .isPresent();
  }

  private static boolean isAccountManager(UserPermission.View permission) {
    return permission.isAccountManager();
  }
}
