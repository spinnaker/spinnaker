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

package com.netflix.spinnaker.gate.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Exercises Gate's owner-local application-list filtering exactly as the {@code
 * ApplicationController} {@code @PostFilter} does: each cached application map is wrapped as a
 * {@link GateApplicationResource} and authorized for {@code READ} through {@link
 * GatePermissionEvaluator} (the kork PDP) using the application's owner-provided embedded ACL.
 *
 * <p>This proves the non-admin filtering works without a Gate-side ACL resolver and without
 * flipping {@code allow-access-to-unknown-applications}: admins see everything, non-admins see only
 * the applications whose embedded ACL grants one of their roles plus unrestricted applications, and
 * applications they cannot read are excluded.
 */
class GateApplicationListAuthorizationTest {

  // Authorization enabled, no ResourceAclResolver (Gate is not the owner), and the strict
  // unknown-application default.
  private final GatePermissionEvaluator evaluator =
      new GatePermissionEvaluator(new SpringAclPolicyDecisionPoint(), null, true, false);

  private static Map<String, Object> app(String name, Map<String, List<String>> permissions) {
    Map<String, Object> app = new LinkedHashMap<>();
    app.put("name", name);
    if (permissions != null) {
      app.put("permissions", permissions);
    }
    return app;
  }

  private static final Map<String, Object> DEPLOY_APP =
      app("deploy-app", Map.of("READ", List.of("deploy-team"), "WRITE", List.of("deploy-team")));
  private static final Map<String, Object> OPS_APP = app("ops-app", Map.of("READ", List.of("ops")));
  // No "permissions" attribute => unrestricted => readable by everyone.
  private static final Map<String, Object> OPEN_APP = app("open-app", null);

  private static final List<Map<String, Object>> ALL_APPS = List.of(DEPLOY_APP, OPS_APP, OPEN_APP);

  private static Authentication user(GrantedAuthority... authorities) {
    return new PreAuthenticatedAuthenticationToken("user", "N/A", List.of(authorities));
  }

  /**
   * Mirrors the {@code @PostFilter("hasPermission(this.asProtectedApplication(filterObject),
   * 'READ')")}.
   */
  private List<String> visibleTo(Authentication authentication) {
    return ALL_APPS.stream()
        .filter(
            app ->
                evaluator.hasPermission(authentication, GateApplicationResource.from(app), "READ"))
        .map(app -> (String) app.get("name"))
        .collect(Collectors.toList());
  }

  @Test
  @DisplayName("admin sees every application (ACL consultation bypassed)")
  void adminSeesAll() {
    assertThat(visibleTo(user(SpinnakerAuthorities.ADMIN_AUTHORITY)))
        .containsExactlyInAnyOrder("deploy-app", "ops-app", "open-app");
  }

  @Test
  @DisplayName(
      "non-admin sees only apps whose embedded ACL grants their role, plus unrestricted apps")
  void nonAdminSeesOnlyPermittedAndUnrestricted() {
    assertThat(visibleTo(user(new SimpleGrantedAuthority("ROLE_deploy-team"))))
        .containsExactlyInAnyOrder("deploy-app", "open-app");
  }

  @Test
  @DisplayName("a different role only unlocks its own restricted app (unpermitted app excluded)")
  void differentRoleSeesItsOwnApp() {
    assertThat(visibleTo(user(new SimpleGrantedAuthority("ROLE_ops"))))
        .containsExactlyInAnyOrder("ops-app", "open-app");
  }

  @Test
  @DisplayName("a user with no granting role still sees unrestricted apps but no restricted ones")
  void noMatchingRoleSeesOnlyUnrestricted() {
    assertThat(visibleTo(user(new SimpleGrantedAuthority("ROLE_someone-else"))))
        .containsExactly("open-app");
  }

  @Test
  @DisplayName("an embedded ACL granting only WRITE does not grant READ visibility in the list")
  void writeOnlyAclDoesNotGrantRead() {
    Map<String, Object> writeOnly = app("write-only", Map.of("WRITE", List.of("deploy-team")));
    boolean visible =
        evaluator.hasPermission(
            user(new SimpleGrantedAuthority("ROLE_deploy-team")),
            GateApplicationResource.from(writeOnly),
            "READ");
    assertThat(visible).isFalse();
  }

  @Test
  @DisplayName(
      "global default roles merged into the embedded ACL by Front50 grant list visibility in Gate")
  void mergedDefaultRoleGrantsVisibility() {
    // Front50 (the owner) additively merges the global default application permissions into the
    // embedded `permissions` it returns, so Gate — which only consumes that embedded ACL — sees the
    // default role and grants visibility without any Gate-side configuration. Here `open-app` had
    // no own ACL and `ops-app` had its own; both end up readable by the global default role.
    Map<String, Object> mergedOpenApp =
        app("open-app", Map.of("READ", List.of("global"), "WRITE", List.of("global")));
    Map<String, Object> mergedOpsApp =
        app("ops-app", Map.of("READ", List.of("ops", "global"), "WRITE", List.of("global")));
    List<Map<String, Object>> mergedApps = List.of(mergedOpenApp, mergedOpsApp);

    List<String> visible =
        mergedApps.stream()
            .filter(
                a ->
                    evaluator.hasPermission(
                        user(new SimpleGrantedAuthority("ROLE_global")),
                        GateApplicationResource.from(a),
                        "READ"))
            .map(a -> (String) a.get("name"))
            .collect(Collectors.toList());

    assertThat(visible).containsExactlyInAnyOrder("open-app", "ops-app");
  }

  @Test
  @DisplayName("GateApplicationResource parses the owner-provided ACL and defaults to unrestricted")
  void wrapperParsesEmbeddedAcl() {
    GateApplicationResource restricted = GateApplicationResource.from(DEPLOY_APP);
    assertThat(restricted.getName()).isEqualTo("deploy-app");
    assertThat(restricted.getPermissions().isRestricted()).isTrue();
    assertThat(restricted.getPermissions().allGroups()).containsExactly("deploy-team");

    GateApplicationResource open = GateApplicationResource.from(OPEN_APP);
    assertThat(open.getPermissions()).isEqualTo(Permissions.EMPTY);
    assertThat(open.getPermissions().isRestricted()).isFalse();
  }
}
