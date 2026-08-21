/*
 * Copyright 2026 Spinnaker.io, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.netflix.spinnaker.clouddriver.security.AccountSecurityPolicy;
import com.netflix.spinnaker.clouddriver.security.AllowAllAccountSecurityPolicy;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Authorizes access to artifact accounts using {@link AccountSecurityPolicy}, the same admin/roles
 * primitives used to enforce access to cloud-provider accounts, evaluated locally against each
 * account's own {@link Permissions} rather than via a Fiat-known resource.
 *
 * <p>READ governs visibility (e.g. account listings); WRITE governs actually using an account
 * (download/fetch/enumerate) -- the same split {@code AccountSecurityPolicy} uses for
 * cloud-provider accounts, where "WRITE permissions are required in order to do anything with an
 * account as the READ permission is only used for certain UI items."
 */
@Component
@NonnullByDefault
public class ArtifactAccountAuthorizer {
  private final AccountSecurityPolicy accountSecurityPolicy;

  public ArtifactAccountAuthorizer(Optional<AccountSecurityPolicy> accountSecurityPolicy) {
    this.accountSecurityPolicy =
        accountSecurityPolicy.orElseGet(AllowAllAccountSecurityPolicy::new);
  }

  public boolean canRead(String username, ArtifactCredentials credentials) {
    return isAuthorized(username, credentials, Authorization.READ);
  }

  public boolean canUse(String username, ArtifactCredentials credentials) {
    return isAuthorized(username, credentials, Authorization.WRITE);
  }

  private boolean isAuthorized(
      String username, ArtifactCredentials credentials, Authorization authorization) {
    Permissions permissions = credentials.getPermissions();
    // Mocks/stubs of ArtifactCredentials built without going through a real constructor (e.g.
    // Mockito.mock) won't run the interface's default getPermissions(), so treat null the same
    // as the documented default: unrestricted.
    if (permissions == null || !permissions.isRestricted()) {
      return true;
    }
    if (accountSecurityPolicy.isAdmin(username)) {
      return true;
    }
    return permissions
        .getAuthorizations(new ArrayList<>(accountSecurityPolicy.getRoles(username)))
        .contains(authorization);
  }
}
