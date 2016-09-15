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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.GroupAccessControlled;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
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
  private List<ResourceProvider> resourceProviders;

  @Override
  public Optional<UserPermission> resolveUnrestrictedUser() {
    return getUserPermission(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME,
                             new ArrayList<>(0) /* groups */);
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
    } catch (ProviderException e) {
      log.warn("Failed to resolve user permission for user " + userId, e);
      return Optional.empty();
    }
    val combo = Stream.concat(roles.stream(), externalRoles.stream())
                      .map(String::toLowerCase)
                      .collect(Collectors.toSet());

    return getUserPermission(userId, combo);
  }

  @SuppressWarnings("unchecked")
  private Optional<UserPermission> getUserPermission(String userId, Collection<String> groups) {
    UserPermission permission = new UserPermission().setId(userId);

    for (ResourceProvider provider : resourceProviders) {
      try {
        if (!groups.isEmpty()) {
          permission.addResources(provider.getAllRestricted(groups));
        } else if (UnrestrictedResourceConfig.UNRESTRICTED_USERNAME.equalsIgnoreCase(userId)) {
          permission.addResources(provider.getAllUnrestricted());
        }
      } catch (ProviderException pe) {
        log.warn("Can't resolve permission for '{}' due to error with '{}': {}",
                 userId,
                 provider.getClass().getSimpleName(),
                 pe.getMessage());
        log.debug("Error resolving UserPermission", pe);
        return Optional.empty();
      }
    }
    return Optional.of(permission);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, UserPermission> resolve(@NonNull Collection<String> userIds) {
    val userToRoles = userRolesProvider.multiLoadRoles(userIds);

    // This is the reverse index of each resourceProvider's getAllRestricted() call.
    Multimap<String, Resource> roleToResource = ArrayListMultimap.create();

    for (ResourceProvider provider : resourceProviders) {
      try {
        provider
            .getAll()
            .forEach(resource -> {
              if (resource instanceof GroupAccessControlled) {
                GroupAccessControlled gacResource = (GroupAccessControlled) resource;
                if (gacResource.getRequiredGroupMembership().isEmpty()) {
                  return; // Unrestricted resources are added later.
                }

                gacResource.getRequiredGroupMembership()
                           .forEach(group -> roleToResource.put(group, gacResource));
              } else if (resource instanceof ServiceAccount) {
                ServiceAccount serviceAccount = (ServiceAccount) resource;
                roleToResource.put(serviceAccount.getNameWithoutDomain(), serviceAccount);
              }
            });
      } catch (ProviderException pe) {
        log.warn("Can't getAllRestricted '{}' resources: {}",
                 provider.getClass().getSimpleName(),
                 pe.getMessage());
        log.debug("Error resolving UserPermission", pe);
        return Collections.emptyMap();
      }
    }

    return userToRoles
        .entrySet()
        .stream()
        .map(entry -> {
          String username = entry.getKey();
          Collection<String> userRoles = entry.getValue();

          UserPermission permission = new UserPermission().setId(username);
          userRoles.forEach(userRole -> permission.addResources(roleToResource.get(userRole)));
          return permission;
        })
        .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
  }
}
