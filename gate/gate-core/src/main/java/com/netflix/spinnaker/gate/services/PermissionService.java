/*
 * Copyright 2016 Google, Inc.
 * Copyright 2023 Apple, Inc.
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

import com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest;
import com.netflix.spinnaker.gate.security.SpinnakerUser;
import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.ServiceAccount;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Authorization facade. Roles are resolved at login (locally, via {@code kork-roles}) and carried
 * in the signed identity token; this service reads them back from the {@link GateIdentityService}
 * role cache. There is no central permission store to consult or sync.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PermissionService {
  private final Front50Service front50Service;

  /**
   * Edge identity facade; resolves and caches a caller's roles. Set by Spring when the
   * identity-token machinery is configured; {@code null} only when authorization is not configured
   * (e.g. unit tests, anonymous/no-auth deployments).
   */
  @Autowired(required = false)
  @Setter
  private GateIdentityService identityService;

  /** Whether owner-local/token-based authorization is configured. */
  public boolean isEnabled() {
    return identityService != null;
  }

  /**
   * Resolve the caller's roles at login (locally, via {@code kork-roles}) and cache them so the
   * identity token can be (re-)minted on each downstream request.
   */
  public void login(final String userId) {
    if (identityService == null) {
      return;
    }
    AuthenticatedRequest.allowAnonymous(
        () -> {
          identityService.resolveAndCacheRoles(userId, List.of());
          return null;
        });
  }

  /**
   * Resolve + merge the caller's roles (provider roles unioned with the roles asserted by the auth
   * mechanism) at login and cache them.
   */
  public void loginWithRoles(final String userId, final Collection<String> roles) {
    if (identityService == null) {
      return;
    }
    AuthenticatedRequest.allowAnonymous(
        () -> {
          identityService.resolveAndCacheRoles(userId, roles);
          return null;
        });
  }

  public void logout(String userId) {
    if (identityService != null) {
      identityService.invalidate(userId);
    }
  }

  /**
   * No-op: there is no central permission store to sync. Roles are resolved at login and carried in
   * the signed identity token. Retained for API compatibility.
   */
  public void sync() {
    // intentionally empty
  }

  public Set<String> getRoles(String userId) {
    if (identityService == null) {
      return Set.of();
    }
    return new LinkedHashSet<>(identityService.rolesFor(userId));
  }

  /**
   * Resolves the principal's roles for API-token authentication. Run under {@code allowAnonymous}
   * since the token filter calls this before the {@code SecurityContext} is populated.
   */
  public Set<String> getRolesForTokenAuth(String userId) {
    if (identityService == null) {
      return Set.of();
    }
    return AuthenticatedRequest.allowAnonymous(
        () -> new LinkedHashSet<>(identityService.rolesFor(userId)));
  }

  /**
   * Whether a role provider is configured. When {@code false}, roles cannot be re-resolved from a
   * principal id alone, so callers must supply their own role source — a service account's Front50
   * {@code memberOf}, or the roles snapshotted on an API token at creation.
   */
  public boolean hasUserRolesResolver() {
    return identityService != null && identityService.hasUserRolesResolver();
  }

  /**
   * Resolve the roles for a managed service-account principal. Service accounts are owned by
   * Front50, so their roles come from the SA's {@code memberOf} (not the user role provider),
   * merged via the same resolver used by login and Front50's run-as token endpoint. The {@code
   * memberOf} lookup is lazy — performed only on a role-cache miss — so high-volume CI traffic
   * using SA tokens doesn't hit Front50 on every request. Run under {@code allowAnonymous} since
   * the token filter calls this before the {@code SecurityContext} is populated.
   */
  public Set<String> resolveServiceAccountRoles(String serviceAccountName) {
    if (identityService == null) {
      return Set.of();
    }
    return AuthenticatedRequest.allowAnonymous(
        () ->
            new LinkedHashSet<>(
                identityService.rolesFor(
                    serviceAccountName, () -> serviceAccountMemberOf(serviceAccountName))));
  }

  /**
   * The Front50-defined {@code memberOf} roles for a managed service account (empty if unknown).
   */
  private List<String> serviceAccountMemberOf(String serviceAccountName) {
    if (serviceAccountName == null || serviceAccountName.isBlank()) {
      return List.of();
    }
    List<ServiceAccount> serviceAccounts;
    try {
      serviceAccounts =
          AuthenticatedRequest.allowAnonymous(
              () -> Retrofit2SyncCall.execute(front50Service.getServiceAccounts()));
    } catch (SpinnakerServerException e) {
      throw UpstreamBadRequest.classifyError(e);
    }
    if (serviceAccounts == null) {
      return List.of();
    }
    return serviceAccounts.stream()
        .filter(sa -> serviceAccountName.equalsIgnoreCase(sa.getName()))
        .findFirst()
        .map(ServiceAccount::getMemberOf)
        .orElse(List.of());
  }

  /**
   * Downstream services enforce their own per-application ACLs, so Gate returns the full set of
   * service accounts the user belongs to.
   */
  public List<String> getServiceAccountsForApplication(
      @SpinnakerUser final User user, @Nonnull final String application) {
    return getServiceAccounts(user);
  }

  /**
   * Returns the names of the Front50-managed service accounts the user may act as — i.e. those
   * whose {@code memberOf} roles intersect the user's resolved roles.
   */
  public List<String> getServiceAccounts(@SpinnakerUser User user) {
    if (user == null) {
      log.debug("getServiceAccounts: Spinnaker user is null.");
      return List.of();
    }
    if (identityService == null) {
      log.debug("getServiceAccounts: authorization disabled.");
      return List.of();
    }

    Set<String> userRoles =
        getRoles(user.getUsername()).stream()
            .filter(Objects::nonNull)
            .map(role -> role.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    if (userRoles.isEmpty()) {
      return List.of();
    }

    List<ServiceAccount> serviceAccounts;
    try {
      serviceAccounts =
          AuthenticatedRequest.allowAnonymous(
              () -> Retrofit2SyncCall.execute(front50Service.getServiceAccounts()));
    } catch (SpinnakerServerException e) {
      throw UpstreamBadRequest.classifyError(e);
    }
    if (serviceAccounts == null) {
      return List.of();
    }
    return serviceAccounts.stream()
        .filter(
            sa ->
                sa.getMemberOf().stream()
                    .filter(Objects::nonNull)
                    .map(role -> role.toLowerCase(Locale.ROOT))
                    .anyMatch(userRoles::contains))
        .map(ServiceAccount::getName)
        .collect(Collectors.toList());
  }

  public boolean isAdmin(String userId) {
    if (identityService == null) {
      return false;
    }
    return identityService.isAdmin(identityService.rolesFor(userId));
  }
}
