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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.spinnaker.fiat.config.FiatAdminConfig;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private List<ResourceProvider<? extends Resource>> resourceProviders;

  @Autowired
  @Setter
  private FiatAdminConfig fiatAdminConfig;

  @Autowired
  @Qualifier("objectMapper")
  @Setter
  private ObjectMapper mapper;

  @Override
  public UserPermission resolveUnrestrictedUser() {
    return getUserPermission(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME,
                             new HashSet<>() /* groups */);
  }

  @Override
  public UserPermission resolve(@NonNull String userId) {
    return resolveAndMerge(new ExternalUser().setId(userId));
  }

  @Override
  public UserPermission resolveAndMerge(@NonNull ExternalUser user) {
    List<Role> roles;
    try {
      log.debug("Loading roles for user " + user);
      roles = userRolesProvider.loadRoles(user);
      log.debug("Got roles " + roles + " for user " + user);
    } catch (ProviderException pe) {
      throw new PermissionResolutionException("Failed to resolve user permission for user " + user.getId(), pe);
    }
    Set<Role> combo = Stream.concat(roles.stream(), user.getExternalRoles().stream())
                            .collect(Collectors.toSet());

    return getUserPermission(user.getId(), combo);
  }

  private boolean resolveAdminRole(Set<Role> roles) {
    List<String> adminRoles = fiatAdminConfig.getAdmin().getRoles();
    return roles.stream().map(Role::getName).anyMatch(adminRoles::contains);
  }

  @SuppressWarnings("unchecked")
  private UserPermission getUserPermission(String userId, Set<Role> roles) {
    UserPermission permission = new UserPermission().setId(userId)
                                                    .setRoles(roles)
                                                    .setAdmin(resolveAdminRole(roles));

    for (ResourceProvider provider : resourceProviders) {
      try {
        if (UnrestrictedResourceConfig.UNRESTRICTED_USERNAME.equalsIgnoreCase(userId)) {
          permission.addResources(provider.getAllUnrestricted());
        } else if (!roles.isEmpty()) {
          permission.addResources(provider.getAllRestricted(roles, permission.isAdmin()));
        }
      } catch (ProviderException pe) {
        throw new PermissionResolutionException(pe);
      }
    }
    return permission;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, UserPermission> resolve(@NonNull Collection<ExternalUser> users) {
    val userToRoles = getAndMergeUserRoles(users);

    return userToRoles
        .entrySet()
        .stream()
        .map(entry -> {
          String username = entry.getKey();
          Set<Role> userRoles = new HashSet<>(entry.getValue());

          return new UserPermission().setId(username)
                                     .setRoles(userRoles)
                                     .setAdmin(resolveAdminRole(userRoles))
                                     .addResources(getResources(userRoles, resolveAdminRole(userRoles)));
        })
        .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
  }

  private Map<String, Collection<Role>> getAndMergeUserRoles(@NonNull Collection<ExternalUser> users) {
    Map<String, Collection<Role>> userToRoles = userRolesProvider.multiLoadRoles(users);

    users.forEach(user -> {
      userToRoles.computeIfAbsent(user.getId(), ignored -> new ArrayList<>())
                 .addAll(user.getExternalRoles());
    });

    if (log.isDebugEnabled()) {
      try {
        log.debug("Multi-loaded roles: \n" + mapper.writerWithDefaultPrettyPrinter()
                                                   .writeValueAsString(userToRoles));
      } catch (Exception e) {
        log.debug("Exception writing roles", e);
      }
    }
    return userToRoles;
  }

  private Set<Resource> getResources(Set<Role> roles, boolean isAdmin) {
    return resourceProviders
        .stream()
        .flatMap(provider -> {
          try {
            return provider.getAllRestricted(roles, isAdmin).stream();
          } catch (ProviderException pe) {
            throw new PermissionResolutionException(pe);
          }
        })
        .collect(Collectors.toSet());
  }
}
