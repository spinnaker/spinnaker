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

import com.netflix.spinnaker.fiat.providers.AccountProvider;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@NoArgsConstructor
@Accessors
public class DefaultPermissionsResolver implements PermissionsResolver {

  @Autowired
  @Setter
  private UserRolesProvider userRolesProvider;

  @Autowired
  @Setter
  private AccountProvider accountProvider;


  @Override
  public UserPermission resolve(@NonNull String userId) {
    val roles = userRolesProvider.loadRoles(userId);
    val accounts = accountProvider.getAccounts(roles);

    return new UserPermission().setId(userId).setAccounts(accounts);
  }

  @Override
  public Map<String, UserPermission> resolve(@NonNull Collection<String> userIds) {
    val roles = userRolesProvider.multiLoadRoles(userIds);
    return roles.entrySet()
                .stream()
                .map(entry ->
                         new UserPermission()
                             .setId(entry.getKey())
                             .setAccounts(accountProvider.getAccounts(new ArrayList<>(entry.getValue()))))
                .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
  }
}
