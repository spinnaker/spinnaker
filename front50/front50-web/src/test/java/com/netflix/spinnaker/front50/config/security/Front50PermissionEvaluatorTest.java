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

package com.netflix.spinnaker.front50.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/** Exercises Front50's {@code canCreate} application-create gate driven by the global defaults. */
class Front50PermissionEvaluatorTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private Front50PermissionEvaluator evaluator(Map<Authorization, Set<String>> defaults) {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();
    props.setDefaultPermissions(defaults);
    return new Front50PermissionEvaluator(
        new SpringAclPolicyDecisionPoint(), null, true, false, props);
  }

  private static void authenticate(GrantedAuthority... authorities) {
    Authentication auth =
        new PreAuthenticatedAuthenticationToken("user", "N/A", List.of(authorities));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private static Map<Authorization, Set<String>> createRoles(String... roles) {
    Map<Authorization, Set<String>> config = new LinkedHashMap<>();
    config.put(Authorization.CREATE, Set.of(roles));
    return config;
  }

  @Test
  @DisplayName("unconfigured CREATE roles keep creation permissive for any authenticated caller")
  void permissiveWhenUnconfigured() {
    Front50PermissionEvaluator evaluator = evaluator(new LinkedHashMap<>());
    authenticate(new SimpleGrantedAuthority("ROLE_anyone"));

    assertThat(evaluator.canCreate("APPLICATION", null)).isTrue();
  }

  @Test
  @DisplayName("denies an unauthenticated caller")
  void deniesUnauthenticated() {
    Front50PermissionEvaluator evaluator = evaluator(createRoles("creators"));
    // No authentication set in the context.
    assertThat(evaluator.canCreate("APPLICATION", null)).isFalse();
  }

  @Test
  @DisplayName("grants when the caller carries a configured CREATE role (case-insensitive)")
  void grantsWhenCallerHasCreateRole() {
    Front50PermissionEvaluator evaluator = evaluator(createRoles("creators"));
    authenticate(new SimpleGrantedAuthority("ROLE_Creators"));

    assertThat(evaluator.canCreate("APPLICATION", null)).isTrue();
  }

  @Test
  @DisplayName("denies when the caller lacks every configured CREATE role")
  void deniesWhenCallerLacksCreateRole() {
    Front50PermissionEvaluator evaluator = evaluator(createRoles("creators"));
    authenticate(new SimpleGrantedAuthority("ROLE_other"));

    assertThat(evaluator.canCreate("APPLICATION", null)).isFalse();
  }

  @Test
  @DisplayName("admin always may create, even without a configured CREATE role")
  void adminBypassesCreateRoleCheck() {
    Front50PermissionEvaluator evaluator = evaluator(createRoles("creators"));
    authenticate(SpinnakerAuthorities.ADMIN_AUTHORITY);

    assertThat(evaluator.canCreate("APPLICATION", null)).isTrue();
  }

  @Test
  @DisplayName("non-application resource creation stays permissive regardless of CREATE config")
  void nonApplicationCreationPermissive() {
    Front50PermissionEvaluator evaluator = evaluator(createRoles("creators"));
    authenticate(new SimpleGrantedAuthority("ROLE_other"));

    assertThat(evaluator.canCreate("SERVICE_ACCOUNT", null)).isTrue();
  }
}
