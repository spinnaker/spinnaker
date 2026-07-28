/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.ServiceAccount;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.User;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import retrofit2.mock.Calls;

/**
 * Exercises the {@link PermissionService}: roles come from the {@link GateIdentityService} role
 * cache (resolved at login via {@code kork-roles}) and service-account membership is filtered
 * locally against the user's roles.
 */
public class PermissionServiceTest {

  @Test
  public void getRolesForTokenAuthRunsUnderAllowAnonymous() {
    // Token auth resolves roles before the SecurityContext is populated; without allowAnonymous a
    // stale X-SPINNAKER-USER in MDC would leak into role resolution.
    Front50Service front50Service = mock(Front50Service.class);
    GateIdentityService identityService = mock(GateIdentityService.class);

    String userId = "alice@example.com";
    AtomicReference<String> anonHeaderDuringCall = new AtomicReference<>();

    when(identityService.rolesFor(userId))
        .thenAnswer(
            invocation -> {
              anonHeaderDuringCall.set(MDC.get(Header.XSpinnakerAnonymous));
              return Set.of("ops");
            });

    MDC.remove(Header.XSpinnakerAnonymous);
    PermissionService subject = new PermissionService(front50Service);
    subject.setIdentityService(identityService);

    try {
      Set<String> roles = subject.getRolesForTokenAuth(userId);

      assertEquals(Set.of("ops"), roles);
      assertEquals(
          "anonymous",
          anonHeaderDuringCall.get(),
          "Role resolution must run with X-SPINNAKER-ANONYMOUS=anonymous in MDC");
      assertNull(
          MDC.get(Header.XSpinnakerAnonymous),
          "MDC anonymous header must be cleared after the call returns");
    } finally {
      MDC.remove(Header.XSpinnakerAnonymous);
    }
  }

  @Test
  public void getRolesForTokenAuthShortCircuitsWhenAuthorizationDisabled() {
    Front50Service front50Service = mock(Front50Service.class);

    // No identity service configured => authorization disabled.
    PermissionService subject = new PermissionService(front50Service);

    assertTrue(subject.getRolesForTokenAuth("alice@example.com").isEmpty());
    assertFalse(subject.isEnabled());
  }

  @Test
  public void getServiceAccountsFiltersByUserRoles() {
    Front50Service front50Service = mock(Front50Service.class);
    GateIdentityService identityService = mock(GateIdentityService.class);

    String userId = "foo@bar.com";
    User user = mock(User.class);
    when(user.getUsername()).thenReturn(userId);

    when(identityService.rolesFor(userId)).thenReturn(Set.of("ops"));
    when(front50Service.getServiceAccounts())
        .thenReturn(Calls.response(List.of(sa("ops-svc", "ops"), sa("dev-svc", "dev"))));

    PermissionService subject = new PermissionService(front50Service);
    subject.setIdentityService(identityService);

    assertEquals(List.of("ops-svc"), subject.getServiceAccounts(user));
  }

  @Test
  public void getServiceAccountsReturnsEmptyWhenAuthorizationDisabled() {
    Front50Service front50Service = mock(Front50Service.class);
    User user = mock(User.class);
    when(user.getUsername()).thenReturn("foo@bar.com");

    PermissionService subject = new PermissionService(front50Service);

    assertTrue(subject.getServiceAccounts(user).isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void resolveServiceAccountRolesUsesFront50MemberOf() {
    Front50Service front50Service = mock(Front50Service.class);
    GateIdentityService identityService = mock(GateIdentityService.class);

    String saName = "ci-bot";
    when(front50Service.getServiceAccounts())
        .thenReturn(
            Calls.response(List.of(sa(saName, "deploy-team", "ops"), sa("other-svc", "dev"))));
    // The SA's memberOf is fed to the identity service as the lazy fallback; resolve it through to
    // verify the correct service account's roles are supplied.
    when(identityService.rolesFor(eq(saName), any(java.util.function.Supplier.class)))
        .thenAnswer(
            invocation -> {
              java.util.function.Supplier<java.util.Collection<String>> supplier =
                  invocation.getArgument(1);
              return new java.util.LinkedHashSet<>(supplier.get());
            });

    PermissionService subject = new PermissionService(front50Service);
    subject.setIdentityService(identityService);

    assertEquals(Set.of("deploy-team", "ops"), subject.resolveServiceAccountRoles(saName));
  }

  @Test
  public void resolveServiceAccountRolesReturnsEmptyWhenAuthorizationDisabled() {
    Front50Service front50Service = mock(Front50Service.class);

    PermissionService subject = new PermissionService(front50Service);

    assertTrue(subject.resolveServiceAccountRoles("ci-bot").isEmpty());
  }

  @Test
  public void isAdminDelegatesToIdentityService() {
    Front50Service front50Service = mock(Front50Service.class);
    GateIdentityService identityService = mock(GateIdentityService.class);

    String userId = "admin@example.com";
    Set<String> roles = Set.of("admins");
    when(identityService.rolesFor(userId)).thenReturn(roles);
    when(identityService.isAdmin(roles)).thenReturn(true);

    PermissionService subject = new PermissionService(front50Service);
    subject.setIdentityService(identityService);

    assertTrue(subject.isAdmin(userId));
  }

  private static ServiceAccount sa(String name, String... memberOf) {
    ServiceAccount serviceAccount = new ServiceAccount();
    serviceAccount.setName(name);
    serviceAccount.setMemberOf(List.of(memberOf));
    return serviceAccount;
  }
}
