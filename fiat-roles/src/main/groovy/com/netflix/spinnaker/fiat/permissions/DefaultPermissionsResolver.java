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

import com.netflix.spinnaker.fiat.config.AnonymousUserConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.providers.AccountProvider;
import com.netflix.spinnaker.fiat.providers.ApplicationProvider;
import com.netflix.spinnaker.fiat.providers.ServiceAccountProvider;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@NoArgsConstructor
@Slf4j
public class DefaultPermissionsResolver implements PermissionsResolver {

  @Autowired
  @Setter
  private UserRolesProvider userRolesProvider;

  @Autowired
  @Setter
  private AccountProvider accountProvider;

  @Autowired
  @Setter
  private ApplicationProvider applicationProvider;

  @Autowired
  @Setter
  private ServiceAccountProvider serviceAccountProvider;

  @Value("${auth.anonymous.enabled}")
  @Setter
  private boolean anonymousEnabled;

  @Override
  public Optional<UserPermission> resolveAnonymous() {
    if (!anonymousEnabled) {
      return Optional.empty();
    }

    val groups = new ArrayList<String>(0);
    val accounts = accountProvider.getAccounts(groups);
    val apps = applicationProvider.getApplications(groups);
    // Anonymous user has no access to service accounts.
    return Optional.of(new UserPermission()
                           .setId(AnonymousUserConfig.ANONYMOUS_USERNAME)
                           .setAccounts(accounts)
                           .setApplications(apps));
  }

  @Override
  public Optional<UserPermission> resolve(@NonNull String userId) {
    return resolveAndMerge(userId, Collections.emptyList());
  }

  @Override
  public Optional<UserPermission> resolveAndMerge(@NonNull String userId, Collection<String> externalRoles) {
    List<String> roles;
    try {
      roles = userRolesProvider.loadRoles(userId);
    } catch (Exception e) {
      log.warn("Failed to resolve user permission for user " + userId, e);
      return Optional.empty();
    }
    val combo = Stream.concat(roles.stream(), externalRoles.stream())
                      .map(String::toLowerCase)
                      .collect(Collectors.toSet());
    val accounts = accountProvider.getAccounts(combo);
    val apps = applicationProvider.getApplications(combo);
    val serviceAccts = serviceAccountProvider.getAccounts(combo);

    return Optional.of(new UserPermission()
                           .setId(userId)
                           .setAccounts(accounts)
                           .setApplications(apps)
                           .setServiceAccounts(serviceAccts));
  }

  @Override
  public Map<String, UserPermission> resolve(@NonNull Collection<String> userIds) {
    val roles = userRolesProvider.multiLoadRoles(userIds);
    return roles.entrySet()
                .stream()
                .map(entry ->
                         new UserPermission()
                             .setId(entry.getKey())
                             .setAccounts(accountProvider.getAccounts(entry.getValue()))
                             .setApplications(applicationProvider.getApplications(entry.getValue()))
                             .setServiceAccounts(serviceAccountProvider.getAccounts(entry.getValue())))
                .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
  }
}
