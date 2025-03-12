/*
 * Copyright 2023 Salesforce, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class DefaultAccountSecurityPolicyTest {
  private static final String username = "testUser";
  private static final String account = "testAccount";
  FiatPermissionEvaluator fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
  DefaultAccountSecurityPolicy policy;

  @BeforeEach
  void setup() {
    policy = new DefaultAccountSecurityPolicy(fiatPermissionEvaluator);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testIsAdmin(boolean isUserAdmin) {
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(isUserAdmin));

    assertEquals(isUserAdmin, policy.isAdmin(username));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testIsAccountManager(boolean isAccountManager) {
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAccountManager(isAccountManager));

    assertEquals(isAccountManager, policy.isAccountManager(username));
  }

  @Test
  public void testGetRoles() {
    Set<String> roles = Set.of("role1", "role2", "role3");
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(
            new UserPermission.View()
                .setRoles(
                    roles.stream()
                        .map(role -> new Role.View().setName(role).setSource(Role.Source.LDAP))
                        .collect(Collectors.toSet())));

    assertEquals(roles, policy.getRoles(username));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCanUseAccount_NotAdmin(boolean hasPermission) {
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(false));
    when(fiatPermissionEvaluator.hasPermission(username, account, "account", Authorization.WRITE))
        .thenReturn(hasPermission);

    assertEquals(hasPermission, policy.canUseAccount(username, account));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCanModifyAccount(boolean isAdmin) {
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(isAdmin));

    assertEquals(isAdmin, policy.canModifyAccount(username, account));
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  public void testCanModifyAccountAsAccountManager(
      boolean isAccountManager, boolean hasWritePermission) {
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(false).setAccountManager(isAccountManager));
    when(fiatPermissionEvaluator.hasPermission(username, account, "account", Authorization.WRITE))
        .thenReturn(hasWritePermission);

    assertEquals(
        isAccountManager && hasWritePermission, policy.canModifyAccount(username, account));
  }
}
