/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.roles;

import com.netflix.spinnaker.security.authz.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * Resolution entrypoint shared by Gate login and the SA/run-as token minter: it resolves a user's
 * roles from the configured {@link UserRolesProvider} and merges them with the roles already
 * asserted by the authentication mechanism ({@link ExternalUser#getExternalRoles()}).
 *
 * <p>This is the relocated, reusable form of {@code
 * DefaultPermissionsResolver.getAndMergeUserRoles} — the union of {@code multiLoadRoles} results
 * with each user's asserted external roles. The external-group merge is pluggable/toggleable via
 * {@link #isMergeExternalRoles()} so deployments that do not want it can disable it.
 */
public class UserRolesResolver {

  private final UserRolesProvider userRolesProvider;
  private final boolean mergeExternalRoles;

  public UserRolesResolver(UserRolesProvider userRolesProvider) {
    this(userRolesProvider, true);
  }

  public UserRolesResolver(UserRolesProvider userRolesProvider, boolean mergeExternalRoles) {
    this.userRolesProvider = userRolesProvider;
    this.mergeExternalRoles = mergeExternalRoles;
  }

  public boolean isMergeExternalRoles() {
    return mergeExternalRoles;
  }

  /** Resolve and (optionally) merge the roles for a single user. */
  @Nonnull
  public Collection<Role> resolveAndMerge(@Nonnull ExternalUser user) {
    Collection<Role> providerRoles = userRolesProvider.loadRoles(user);
    if (!mergeExternalRoles) {
      return new LinkedHashSet<>(providerRoles);
    }
    Set<Role> merged =
        Stream.concat(providerRoles.stream(), user.getExternalRoles().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return merged;
  }

  /**
   * Resolve and (optionally) merge the roles for multiple users, keyed by user id. Mirrors {@code
   * getAndMergeUserRoles}: {@code multiLoadRoles} unioned with each user's asserted external roles.
   */
  @Nonnull
  public Map<String, Collection<Role>> multiResolveAndMerge(
      @Nonnull Collection<ExternalUser> users) {
    Map<String, Collection<Role>> userToRoles =
        new HashMap<>(userRolesProvider.multiLoadRoles(users));

    if (mergeExternalRoles) {
      users.forEach(
          user ->
              userToRoles
                  .computeIfAbsent(user.getId(), ignored -> new ArrayList<>())
                  .addAll(user.getExternalRoles()));
    }
    return userToRoles;
  }

  /** The role names a token would carry for a single user (lower-cased, de-duplicated). */
  @Nonnull
  public Set<String> resolveRoleNames(@Nonnull ExternalUser user) {
    return resolveAndMerge(user).stream()
        .map(Role::getName)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
