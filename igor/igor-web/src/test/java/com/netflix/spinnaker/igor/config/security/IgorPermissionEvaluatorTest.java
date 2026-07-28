/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.igor.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Focused owner-local authorization tests: the {@link IgorPermissionEvaluator} resolves
 * build-service ACLs straight from Igor's own {@link BuildServices} / GCB registries and decides
 * via the Spring ACL {@link SpringAclPolicyDecisionPoint}.
 */
class IgorPermissionEvaluatorTest {

  private static final String BUILD_SERVICE = "BUILD_SERVICE";

  private BuildServices buildServices;
  private IgorResourceAclResolver resolver;

  @BeforeEach
  void setup() {
    buildServices = new BuildServices();

    // An unrestricted build service: no ACL groups -> everyone may access.
    BuildOperations open = mock(BuildOperations.class);
    when(open.getPermissions()).thenReturn(Permissions.EMPTY);

    // A restricted build service: only members of the "dev" role may READ; "ops" may WRITE.
    BuildOperations restricted = mock(BuildOperations.class);
    when(restricted.getPermissions())
        .thenReturn(
            new Permissions.Builder()
                .add(Authorization.READ, "dev")
                .add(Authorization.WRITE, "ops")
                .build());

    buildServices.addServices(java.util.Map.of("open", open, "restricted", restricted));

    resolver = new IgorResourceAclResolver(buildServices, Optional.empty());
  }

  private IgorPermissionEvaluator evaluator(boolean allowUnknown) {
    return new IgorPermissionEvaluator(new SpringAclPolicyDecisionPoint(), resolver, allowUnknown);
  }

  private Authentication user(String... roles) {
    List<GrantedAuthority> authorities =
        java.util.Arrays.stream(roles)
            .map(SpinnakerAuthorities::forRoleName)
            .collect(java.util.stream.Collectors.toList());
    return new PreAuthenticatedAuthenticationToken("alice", "N/A", authorities);
  }

  private Authentication admin() {
    return new PreAuthenticatedAuthenticationToken(
        "root", "N/A", List.of(SpinnakerAuthorities.ADMIN_AUTHORITY));
  }

  @Test
  void unrestrictedBuildServiceIsReadableByAnyone() {
    IgorPermissionEvaluator evaluator = evaluator(true);
    assertThat(evaluator.hasPermission(user(), "open", BUILD_SERVICE, "READ")).isTrue();
    assertThat(evaluator.hasPermission(user("someoneelse"), "open", BUILD_SERVICE, "WRITE"))
        .isTrue();
  }

  @Test
  void restrictedBuildServiceEnforcesRoles() {
    IgorPermissionEvaluator evaluator = evaluator(true);

    // dev may READ but not WRITE; ops may WRITE.
    assertThat(evaluator.hasPermission(user("dev"), "restricted", BUILD_SERVICE, "READ")).isTrue();
    assertThat(evaluator.hasPermission(user("dev"), "restricted", BUILD_SERVICE, "WRITE"))
        .isFalse();
    assertThat(evaluator.hasPermission(user("ops"), "restricted", BUILD_SERVICE, "WRITE")).isTrue();

    // A caller with no matching role is denied.
    assertThat(evaluator.hasPermission(user("other"), "restricted", BUILD_SERVICE, "READ"))
        .isFalse();
  }

  @Test
  void adminBypassesRestrictedBuildServiceAcl() {
    IgorPermissionEvaluator evaluator = evaluator(true);
    assertThat(evaluator.hasPermission(admin(), "restricted", BUILD_SERVICE, "WRITE")).isTrue();
  }

  @Test
  void unknownBuildServiceIsPermissiveByDefault() {
    IgorPermissionEvaluator evaluator = evaluator(true);
    assertThat(evaluator.hasPermission(user("dev"), "does-not-exist", BUILD_SERVICE, "READ"))
        .isTrue();
  }

  @Test
  void unknownBuildServiceIsDeniedWhenPermissiveFallbackDisabled() {
    IgorPermissionEvaluator evaluator = evaluator(false);
    assertThat(evaluator.hasPermission(user("dev"), "does-not-exist", BUILD_SERVICE, "READ"))
        .isFalse();
  }

  @Test
  void resolverIgnoresNonBuildServiceResourceTypes() {
    assertThat(resolver.resolve(ResourceType.APPLICATION, "open")).isNull();
    assertThat(resolver.resolve(ResourceType.ACCOUNT, "open")).isNull();
  }

  @Test
  void resolverReadsBuildServiceAclFromOwnRegistry() {
    Permissions acl = resolver.resolve(ResourceType.BUILD_SERVICE, "restricted");
    assertThat(acl).isNotNull();
    assertThat(acl.isRestricted()).isTrue();
    assertThat(acl.get(Authorization.READ)).containsExactly("dev");
  }

  @Test
  void resolverFallsBackToGoogleCloudBuildAccounts() {
    GoogleCloudBuildProperties gcb = new GoogleCloudBuildProperties();
    GoogleCloudBuildProperties.Account account =
        GoogleCloudBuildProperties.Account.builder()
            .name("gcb-account")
            .project("gcb-project")
            .permissions(new Permissions.Builder().add(Authorization.READ, "gcb-readers"))
            .build();
    gcb.setAccounts(Collections.singletonList(account));

    IgorResourceAclResolver gcbResolver =
        new IgorResourceAclResolver(buildServices, Optional.of(gcb));
    Permissions acl = gcbResolver.resolve(ResourceType.BUILD_SERVICE, "gcb-account");
    assertThat(acl).isNotNull();
    assertThat(acl.get(Authorization.READ)).containsExactly("gcb-readers");
  }
}
