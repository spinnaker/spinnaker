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
import org.junit.jupiter.params.provider.ValueSource;

public class DefaultAccountSecurityPolicyTest {
  private static final String username = "testUser";
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
    // TODO: replace with UserPermission.View::setAccountManager after fiat updated
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(isAccountManager));

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
    String account = "testAccount";
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(false));
    when(fiatPermissionEvaluator.hasPermission(username, account, "account", Authorization.WRITE))
        .thenReturn(hasPermission);

    assertEquals(hasPermission, policy.canUseAccount(username, account));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCanModifyAccount(boolean isAdmin) {
    /* TODO: when isAccountManager uses the account manager value, update to get value from
    hasPermission mock. */
    when(fiatPermissionEvaluator.getPermission(username))
        .thenReturn(new UserPermission.View().setAdmin(isAdmin));

    assertEquals(isAdmin, policy.canModifyAccount(username, "testAccount"));
  }
}
