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

package com.netflix.spinnaker.fiat.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.fiat.model.resources.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

class UserPermissionTest {
  @Test
  void rolesConvertToGrantedAuthorities() {
    Role first = new Role("first");
    Role second = new Role("second");
    Role third = new Role("third");
    UserPermission userPermission =
        new UserPermission().setId("jesse").setRoles(Set.of(first, second, third));
    Set<GrantedAuthority> authorities = userPermission.getView().toGrantedAuthorities();
    Set<String> result = AuthorityUtils.authorityListToSet(authorities);
    assertThat(result).containsExactlyInAnyOrder("ROLE_first", "ROLE_second", "ROLE_third");
  }

  @Test
  void adminRoleConvertsToGrantedAuthority() {
    UserPermission userPermission = new UserPermission().setId("sherry").setAdmin(true);
    Set<GrantedAuthority> authorities = userPermission.getView().toGrantedAuthorities();
    assertThat(authorities).containsExactly(SpinnakerAuthorities.ADMIN_AUTHORITY);
  }

  @Test
  void accountManagerConvertsToGrantedAuthority() {
    UserPermission userPermission = new UserPermission().setId("ralph").setAccountManager(true);
    Set<GrantedAuthority> authorities = userPermission.getView().toGrantedAuthorities();
    assertThat(authorities).containsExactly(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
  }
}
