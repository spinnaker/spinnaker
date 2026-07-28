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

package com.netflix.spinnaker.orca.web.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Orca's execution endpoints are the only enforcement point for execution data — nothing downstream
 * re-checks a list Orca has already served — so these assert that a caller's roles actually gate
 * access, rather than every authenticated caller passing.
 */
class OrcaPermissionEvaluatorTest {

  private static final Permissions TEAM_A_ONLY =
      new Permissions.Builder()
          .add(Authorization.READ, "team-a")
          .add(Authorization.EXECUTE, "team-a")
          .build();

  /** Resolves one known application; everything else is unresolvable. */
  private static ResourceAclResolver resolverFor(String application, @Nullable Permissions acl) {
    return (resourceType, resourceName) ->
        ResourceType.APPLICATION.equals(resourceType) && application.equals(resourceName)
            ? acl
            : null;
  }

  private static Authentication callerWithRoles(String... roles) {
    List<GrantedAuthority> authorities =
        Arrays.stream(roles).map(SpinnakerAuthorities::forRoleName).collect(Collectors.toList());
    return new PreAuthenticatedAuthenticationToken("caller@example.com", "N/A", authorities);
  }

  private static Authentication admin() {
    return new PreAuthenticatedAuthenticationToken(
        "admin@example.com", "N/A", List.of(SpinnakerAuthorities.ADMIN_AUTHORITY));
  }

  private static OrcaPermissionEvaluator evaluator(
      @Nullable ResourceAclResolver resolver,
      boolean enabled,
      boolean allowAccessToUnknownApplications) {
    return new OrcaPermissionEvaluator(
        new SpringAclPolicyDecisionPoint(), resolver, enabled, allowAccessToUnknownApplications);
  }

  @Test
  @DisplayName("a caller holding a granted role may read the application's executions")
  void grantsCallerWithMatchingRole() {
    OrcaPermissionEvaluator evaluator =
        evaluator(resolverFor("dd-logme", TEAM_A_ONLY), true, false);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-a"), "dd-logme", "APPLICATION", Authorization.READ.name()))
        .isTrue();
  }

  @Test
  @DisplayName("a caller without a granted role is denied, rather than passing as before")
  void deniesCallerWithoutMatchingRole() {
    OrcaPermissionEvaluator evaluator =
        evaluator(resolverFor("dd-logme", TEAM_A_ONLY), true, false);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-b"), "dd-logme", "APPLICATION", Authorization.READ.name()))
        .isFalse();
  }

  @Test
  @DisplayName("cancelling another team's execution requires the EXECUTE grant")
  void deniesMutatingAnotherTeamsExecution() {
    OrcaPermissionEvaluator evaluator =
        evaluator(resolverFor("dd-logme", TEAM_A_ONLY), true, false);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-b"), "dd-logme", "APPLICATION", Authorization.EXECUTE.name()))
        .isFalse();
    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-a"), "dd-logme", "APPLICATION", Authorization.EXECUTE.name()))
        .isTrue();
  }

  @Test
  @DisplayName("an unrestricted application is readable by any caller")
  void allowsUnrestrictedApplication() {
    OrcaPermissionEvaluator evaluator =
        evaluator(resolverFor("open-app", Permissions.EMPTY), true, false);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-b"), "open-app", "APPLICATION", Authorization.READ.name()))
        .isTrue();
  }

  @Test
  @DisplayName("admins bypass the ACL")
  void allowsAdmin() {
    OrcaPermissionEvaluator evaluator =
        evaluator(resolverFor("dd-logme", TEAM_A_ONLY), true, false);

    assertThat(evaluator.hasPermission(admin(), "dd-logme", "APPLICATION", "READ")).isTrue();
  }

  @Test
  @DisplayName("an unresolvable application is denied when unknown-application access is off")
  void deniesUnresolvableApplicationWhenConfiguredStrict() {
    // Front50 unreachable, or an application with no record and no global defaults configured.
    OrcaPermissionEvaluator evaluator = evaluator(resolverFor("dd-logme", null), true, false);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-a"), "dd-logme", "APPLICATION", Authorization.READ.name()))
        .isFalse();
  }

  @Test
  @DisplayName("an unresolvable application is allowed when unknown-application access is on")
  void allowsUnresolvableApplicationWhenConfiguredPermissive() {
    OrcaPermissionEvaluator evaluator = evaluator(resolverFor("dd-logme", null), true, true);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-a"), "dd-logme", "APPLICATION", Authorization.READ.name()))
        .isTrue();
  }

  @Test
  @DisplayName("every check short-circuits to allow when authorization is disabled")
  void allowsEverythingWhenAuthorizationDisabled() {
    OrcaPermissionEvaluator evaluator =
        evaluator(resolverFor("dd-logme", TEAM_A_ONLY), false, false);

    assertThat(
            evaluator.hasPermission(
                callerWithRoles("team-b"), "dd-logme", "APPLICATION", Authorization.READ.name()))
        .isTrue();
  }
}
