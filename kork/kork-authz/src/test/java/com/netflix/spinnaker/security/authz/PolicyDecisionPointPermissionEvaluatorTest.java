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

package com.netflix.spinnaker.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Exercises the PEP seam: admin / account-manager short-circuits and unknown-application handling.
 */
class PolicyDecisionPointPermissionEvaluatorTest {

  private static final Permissions RESTRICTED_ACCOUNT =
      new Permissions.Builder().add(Authorization.WRITE, "ops").build();

  private final SpringAclPolicyDecisionPoint pdp = new SpringAclPolicyDecisionPoint();

  private static class App implements ProtectedResource {
    private final String name;
    private final Permissions permissions;

    App(String name, Permissions permissions) {
      this.name = name;
      this.permissions = permissions;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public ResourceType getResourceType() {
      return ResourceType.APPLICATION;
    }

    @Override
    public Permissions getPermissions() {
      return permissions;
    }
  }

  private static Authentication user(GrantedAuthority... authorities) {
    return new PreAuthenticatedAuthenticationToken("user", "N/A", List.of(authorities));
  }

  @Test
  void adminBypassesAclConsultation() {
    PolicyDecisionPointPermissionEvaluator evaluator =
        new PolicyDecisionPointPermissionEvaluator(pdp);
    App restricted =
        new App("locked", new Permissions.Builder().add(Authorization.WRITE, "ops").build());

    assertThat(
            evaluator.hasPermission(
                user(SpinnakerAuthorities.ADMIN_AUTHORITY), restricted, "WRITE"))
        .isTrue();
    assertThat(
            evaluator.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_dev")), restricted, "WRITE"))
        .isFalse();
  }

  @Test
  void accountManagerBypassesAccountAclByIdPath() {
    ResourceAclResolver resolver = (type, name) -> RESTRICTED_ACCOUNT;
    PolicyDecisionPointPermissionEvaluator evaluator =
        new PolicyDecisionPointPermissionEvaluator(pdp, resolver, true, false);

    assertThat(
            evaluator.hasPermission(
                user(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY), "prod", "account", "WRITE"))
        .isTrue();
    assertThat(
            evaluator.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_dev")), "prod", "account", "WRITE"))
        .isFalse();
  }

  @Test
  void grantsResourceAccessByEmbeddedAcl() {
    PolicyDecisionPointPermissionEvaluator evaluator =
        new PolicyDecisionPointPermissionEvaluator(pdp);
    App app =
        new App("spinnaker", new Permissions.Builder().add(Authorization.READ, "viewers").build());

    assertThat(
            evaluator.hasPermission(user(new SimpleGrantedAuthority("ROLE_viewers")), app, "READ"))
        .isTrue();
    assertThat(
            evaluator.hasPermission(user(new SimpleGrantedAuthority("ROLE_viewers")), app, "WRITE"))
        .isFalse();
  }

  @Test
  void unknownApplicationHonorsFlagOnByIdPath() {
    ResourceAclResolver missing = (type, name) -> null;

    PolicyDecisionPointPermissionEvaluator permissive =
        new PolicyDecisionPointPermissionEvaluator(pdp, missing, true, true);
    assertThat(
            permissive.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_dev")), "ghost", "application", "READ"))
        .isTrue();

    PolicyDecisionPointPermissionEvaluator strict =
        new PolicyDecisionPointPermissionEvaluator(pdp, missing, true, false);
    assertThat(
            strict.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_dev")), "ghost", "application", "READ"))
        .isFalse();
  }
}
