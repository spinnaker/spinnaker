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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.authz.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Validates the external-group merge entrypoint (relocated getAndMergeUserRoles). */
class UserRolesResolverTest {

  /** A simple provider returning a fixed set of provider-resolved roles per user id. */
  private static class StubProvider implements UserRolesProvider {
    private final Map<String, Collection<Role>> rolesById;

    StubProvider(Map<String, Collection<Role>> rolesById) {
      this.rolesById = rolesById;
    }

    @Override
    public List<Role> loadRoles(ExternalUser user) {
      return new java.util.ArrayList<>(
          rolesById.getOrDefault(user.getId(), java.util.Collections.emptyList()));
    }

    @Override
    public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
      return users.stream()
          .collect(
              Collectors.toMap(
                  ExternalUser::getId,
                  u ->
                      new java.util.ArrayList<>(
                          rolesById.getOrDefault(u.getId(), java.util.Collections.emptyList()))));
    }
  }

  private static ExternalUser userWithExternalRoles(String id, String... externalRoles) {
    ExternalUser user = new ExternalUser();
    user.setId(id);
    user.setExternalRoles(
        java.util.Arrays.stream(externalRoles)
            .map(r -> new Role(r).setSource(Role.Source.EXTERNAL))
            .collect(Collectors.toList()));
    return user;
  }

  @Test
  void mergesProviderRolesWithExternalRoles() {
    StubProvider provider =
        new StubProvider(
            Map.of("alice", List.of(new Role("ldap-team").setSource(Role.Source.LDAP))));
    UserRolesResolver resolver = new UserRolesResolver(provider, true);

    Collection<Role> merged =
        resolver.resolveAndMerge(userWithExternalRoles("alice", "saml-group"));

    assertThat(merged.stream().map(Role::getName).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("ldap-team", "saml-group");
  }

  @Test
  void omitsExternalRolesWhenMergeDisabled() {
    StubProvider provider =
        new StubProvider(
            Map.of("alice", List.of(new Role("ldap-team").setSource(Role.Source.LDAP))));
    UserRolesResolver resolver = new UserRolesResolver(provider, false);

    Collection<Role> roles = resolver.resolveAndMerge(userWithExternalRoles("alice", "saml-group"));

    assertThat(roles.stream().map(Role::getName).collect(Collectors.toSet()))
        .containsExactly("ldap-team");
  }

  @Test
  void multiResolveMergesPerUser() {
    StubProvider provider =
        new StubProvider(
            Map.of(
                "alice", List.of(new Role("ldap-a")),
                "bob", List.of(new Role("ldap-b"))));
    UserRolesResolver resolver = new UserRolesResolver(provider, true);

    Map<String, Collection<Role>> result =
        resolver.multiResolveAndMerge(
            List.of(userWithExternalRoles("alice", "saml-a"), userWithExternalRoles("bob")));

    assertThat(result.get("alice").stream().map(Role::getName).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("ldap-a", "saml-a");
    assertThat(result.get("bob").stream().map(Role::getName).collect(Collectors.toSet()))
        .containsExactly("ldap-b");
  }
}
