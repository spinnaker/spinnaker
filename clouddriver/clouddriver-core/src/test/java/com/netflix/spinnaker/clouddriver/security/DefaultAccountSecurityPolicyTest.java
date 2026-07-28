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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Verifies the owner-local {@link DefaultAccountSecurityPolicy}: decisions are derived from the
 * caller's authorities in the {@code SecurityContext} (populated from the verified identity token)
 * plus the {@link PolicyDecisionPointPermissionEvaluator}, with no remote permission lookup.
 */
public class DefaultAccountSecurityPolicyTest {
  private static final String username = "testUser";
  private static final String account = "testAccount";

  PolicyDecisionPointPermissionEvaluator permissionEvaluator =
      mock(PolicyDecisionPointPermissionEvaluator.class);
  DefaultAccountSecurityPolicy policy;

  @BeforeEach
  void setup() {
    policy = new DefaultAccountSecurityPolicy(permissionEvaluator);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(GrantedAuthority... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new PreAuthenticatedAuthenticationToken(username, "N/A", List.of(authorities)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testIsAdmin(boolean isUserAdmin) {
    if (isUserAdmin) {
      authenticate(SpinnakerAuthorities.ADMIN_AUTHORITY);
    } else {
      authenticate();
    }

    assertEquals(isUserAdmin, policy.isAdmin(username));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testIsAccountManager(boolean isAccountManager) {
    if (isAccountManager) {
      authenticate(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
    } else {
      authenticate();
    }

    assertEquals(isAccountManager, policy.isAccountManager(username));
  }

  @Test
  public void testGetRoles() {
    Set<String> roles = Set.of("role1", "role2", "role3");
    List<GrantedAuthority> authorities = new ArrayList<>();
    roles.forEach(role -> authorities.add(SpinnakerAuthorities.forRoleName(role)));
    authenticate(authorities.toArray(new GrantedAuthority[0]));

    assertEquals(roles, Set.copyOf(policy.getRoles(username)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCanUseAccount_NotAdmin(boolean hasPermission) {
    authenticate();
    when(permissionEvaluator.hasPermission(
            any(), eq(account), eq(ResourceType.ACCOUNT.getName()), eq(Authorization.WRITE)))
        .thenReturn(hasPermission);

    assertEquals(hasPermission, policy.canUseAccount(username, account));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCanModifyAccount(boolean isAdmin) {
    if (isAdmin) {
      authenticate(SpinnakerAuthorities.ADMIN_AUTHORITY);
    } else {
      authenticate();
    }

    assertEquals(isAdmin, policy.canModifyAccount(username, account));
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  public void testCanModifyAccountAsAccountManager(
      boolean isAccountManager, boolean hasWritePermission) {
    if (isAccountManager) {
      authenticate(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
    } else {
      authenticate();
    }
    when(permissionEvaluator.hasPermission(
            any(), eq(account), eq(ResourceType.ACCOUNT.getName()), eq(Authorization.WRITE)))
        .thenReturn(hasWritePermission);

    assertEquals(
        isAccountManager && hasWritePermission, policy.canModifyAccount(username, account));
  }
}
