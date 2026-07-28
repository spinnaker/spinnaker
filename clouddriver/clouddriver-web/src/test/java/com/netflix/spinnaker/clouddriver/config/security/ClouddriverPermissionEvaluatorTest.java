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

package com.netflix.spinnaker.clouddriver.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Verifies Clouddriver's enforcement once a resolver supplies both {@code account} ACLs (owned
 * locally) and {@code application} ACLs (resolved from Front50): account and application checks
 * evaluate against real permissions, while an application that still cannot be resolved honors the
 * configured {@code allowAccessToUnknownApplications} fallback.
 */
class ClouddriverPermissionEvaluatorTest {

  /**
   * Stands in for the composed resolver: {@code prod} account grants WRITE to {@code ops}, {@code
   * service-template} application grants READ to {@code svc}, everything else is unresolved ({@code
   * null}).
   */
  private static final ResourceAclResolver RESOLVER =
      (type, name) -> {
        if (ResourceType.ACCOUNT.equals(type) && "prod".equals(name)) {
          return new Permissions.Builder().add(Authorization.WRITE, "ops").build();
        }
        if (ResourceType.APPLICATION.equals(type) && "service-template".equals(name)) {
          return new Permissions.Builder().add(Authorization.READ, "svc").build();
        }
        return null;
      };

  private final SpringAclPolicyDecisionPoint pdp = new SpringAclPolicyDecisionPoint();

  private static Authentication user(GrantedAuthority... authorities) {
    return new PreAuthenticatedAuthenticationToken("user", "N/A", List.of(authorities));
  }

  private ClouddriverPermissionEvaluator evaluator(boolean allowAccessToUnknownApplications) {
    return new ClouddriverPermissionEvaluator(
        pdp, RESOLVER, true, allowAccessToUnknownApplications);
  }

  @Test
  void evaluatesApplicationAclResolvedFromOwner() {
    ClouddriverPermissionEvaluator evaluator = evaluator(false);
    assertThat(
            evaluator.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_svc")),
                "service-template",
                "application",
                "READ"))
        .isTrue();
    assertThat(
            evaluator.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_dev")),
                "service-template",
                "application",
                "READ"))
        .isFalse();
  }

  @Test
  void enforcesAccountAcl() {
    ClouddriverPermissionEvaluator evaluator = evaluator(false);
    assertThat(
            evaluator.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_ops")), "prod", "account", "WRITE"))
        .isTrue();
    assertThat(
            evaluator.hasPermission(
                user(new SimpleGrantedAuthority("ROLE_dev")), "prod", "account", "WRITE"))
        .isFalse();
  }

  @Test
  void unresolvedApplicationHonorsFlag() {
    Authentication dev = user(new SimpleGrantedAuthority("ROLE_dev"));
    assertThat(evaluator(false).hasPermission(dev, "ghost", "application", "READ")).isFalse();
    assertThat(evaluator(true).hasPermission(dev, "ghost", "application", "READ")).isTrue();
  }

  @Test
  void unknownAccountIsAlwaysDenied() {
    // The flag is application-scoped: an unresolved account is denied regardless.
    Authentication dev = user(new SimpleGrantedAuthority("ROLE_dev"));
    assertThat(evaluator(true).hasPermission(dev, "ghost", "account", "READ")).isFalse();
  }

  @Test
  void adminBypassesAllChecks() {
    Authentication admin = user(SpinnakerAuthorities.ADMIN_AUTHORITY);
    assertThat(evaluator(false).hasPermission(admin, "prod", "account", "WRITE")).isTrue();
    assertThat(evaluator(false).hasPermission(admin, "ghost", "account", "READ")).isTrue();
    assertThat(evaluator(false).hasPermission(admin, "ghost", "application", "READ")).isTrue();
  }
}
