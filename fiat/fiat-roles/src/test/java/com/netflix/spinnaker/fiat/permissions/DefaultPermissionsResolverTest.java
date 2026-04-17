/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use it except in compliance with the License.
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

package com.netflix.spinnaker.fiat.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.config.AccountManagerConfig;
import com.netflix.spinnaker.fiat.config.FiatAdminConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultPermissionsResolverTest {

  @Test
  void resolveResourcesShouldHandleManyUsersInParallel() {
    Role role = new Role("test-role");
    @SuppressWarnings("unchecked")
    ResourceProvider<Application> applicationProvider = mock(ResourceProvider.class);

    Map<String, Collection<Role>> userToRoles = new HashMap<>();
    for (int i = 1; i <= 50; i++) {
      userToRoles.put("user" + i, List.of(role));
    }

    when(applicationProvider.getAllRestricted(any(), anySet(), anyBoolean()))
        .thenAnswer(
            invocation -> {
              String userId = invocation.getArgument(0);
              return Set.of(new Application().setName("app-" + userId));
            });

    UserRolesProvider userRolesProvider = mock(UserRolesProvider.class);
    ResourceProvider<ServiceAccount> serviceAccountProvider = mock(ResourceProvider.class);
    List<ResourceProvider<? extends Resource>> resourceProviders = new ArrayList<>();
    resourceProviders.add(applicationProvider);

    DefaultPermissionsResolver resolver =
        new DefaultPermissionsResolver(
            userRolesProvider,
            serviceAccountProvider,
            resourceProviders,
            new FiatAdminConfig(),
            new AccountManagerConfig(),
            new ObjectMapper());

    Map<String, UserPermission> result = resolver.resolveResources(userToRoles);

    assertEquals(50, result.size());
    for (int i = 1; i <= 50; i++) {
      String userId = "user" + i;
      UserPermission perm = result.get(userId);
      assertNotNull(perm, "User " + userId + " should be in result");
      assertEquals(userId, perm.getId());
      assertEquals(Set.of(role), perm.getRoles());
      assertTrue(
          perm.getApplications().stream().anyMatch(a -> a.getName().equals("app-" + userId)),
          "User " + userId + " should have app-" + userId);
    }
  }
}
