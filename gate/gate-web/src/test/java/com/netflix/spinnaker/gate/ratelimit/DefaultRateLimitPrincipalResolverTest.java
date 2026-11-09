/*
 * Copyright 2026 Spinnaker Contributors
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
package com.netflix.spinnaker.gate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class DefaultRateLimitPrincipalResolverTest {

  private final DefaultRateLimitPrincipalResolver resolver =
      new DefaultRateLimitPrincipalResolver(List.of());

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rolesComeFromRoleAuthorities() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user", "n/a", AuthorityUtils.createAuthorityList("ROLE_ci-bots", "ROLE_devs")));

    assertThat(resolver.resolveRoles(new MockHttpServletRequest()))
        .containsExactlyInAnyOrder("ci-bots", "devs");
  }

  @Test
  void adminsCarryTheAdminRole() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "root",
                "n/a",
                List.of(
                    SpinnakerAuthorities.ADMIN_AUTHORITY,
                    SpinnakerAuthorities.forRoleName("devs"))));

    assertThat(resolver.resolveRoles(new MockHttpServletRequest()))
        .containsExactlyInAnyOrder(SpinnakerAuthorities.ADMIN, "devs");
  }

  @Test
  void anonymousAndUnauthenticatedCallersHaveNoRoles() {
    assertThat(resolver.resolveRoles(new MockHttpServletRequest())).isEmpty();

    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThat(resolver.resolveRoles(new MockHttpServletRequest())).isEmpty();
  }

  @Test
  void fiatRolesIncludeGroupsThatAreNotGrantedAuthorities() {
    PermissionService permissionService = mock(PermissionService.class);
    when(permissionService.getRoles("user"))
        .thenReturn(
            Set.of(new Role.View(new Role("ad-hoc-group")), new Role.View(new Role("devs"))));
    DefaultRateLimitPrincipalResolver withFiat =
        new DefaultRateLimitPrincipalResolver(List.of(), permissionService);

    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user", "n/a", AuthorityUtils.createAuthorityList("ROLE_devs")));

    assertThat(withFiat.resolveRoles(new MockHttpServletRequest()))
        .containsExactlyInAnyOrder("ad-hoc-group", "devs");
  }

  @Test
  void aFailedFiatLookupFallsBackToAuthorities() {
    PermissionService permissionService = mock(PermissionService.class);
    when(permissionService.getRoles("user")).thenThrow(new IllegalStateException("fiat is down"));
    DefaultRateLimitPrincipalResolver withFiat =
        new DefaultRateLimitPrincipalResolver(List.of(), permissionService);

    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user", "n/a", AuthorityUtils.createAuthorityList("ROLE_devs")));

    assertThat(withFiat.resolveRoles(new MockHttpServletRequest())).containsExactly("devs");
  }
}
