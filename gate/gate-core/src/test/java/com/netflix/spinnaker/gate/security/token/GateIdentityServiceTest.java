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

package com.netflix.spinnaker.gate.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.security.roles.ExternalUser;
import com.netflix.spinnaker.security.roles.UserRolesResolver;
import com.netflix.spinnaker.security.token.SpinnakerTokenMinter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GateIdentityServiceTest {

  private final UserRolesResolver userRolesResolver = mock(UserRolesResolver.class);
  private final SpinnakerTokenMinter tokenMinter = mock(SpinnakerTokenMinter.class);

  private static final String USER = "alice@doordash.com";

  private GateAuthzProperties props() {
    GateAuthzProperties p = new GateAuthzProperties();
    p.setAdminRoles(List.of("spinnaker-admins"));
    p.setAccountManagerRoles(List.of("account-managers"));
    return p;
  }

  // ---------------------------------------------------------------------------
  // Role resolution
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("no UserRolesResolver configured — trusts the asserted roles (lower-cased)")
  void noResolverTrustsAssertedRoles() {
    GateIdentityService svc = new GateIdentityService(null, null, props());

    Set<String> roles = svc.resolveAndCacheRoles(USER, List.of("Deploy-Team", "OPS"));

    assertThat(roles).containsExactlyInAnyOrder("deploy-team", "ops");
  }

  @Test
  @DisplayName("resolver configured — delegates to kork-roles and merges the asserted roles in")
  void resolverDelegatesToKorkRoles() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenReturn(Set.of("deploy-team", "platform"));
    GateIdentityService svc = new GateIdentityService(userRolesResolver, null, props());

    Set<String> roles = svc.resolveAndCacheRoles(USER, List.of("deploy-team"));

    assertThat(roles).containsExactlyInAnyOrder("deploy-team", "platform");

    // The asserted roles are forwarded to the resolver as EXTERNAL roles for merge.
    ArgumentCaptor<ExternalUser> captor = ArgumentCaptor.forClass(ExternalUser.class);
    verify(userRolesResolver).resolveRoleNames(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(USER);
    assertThat(captor.getValue().getExternalRoles()).extracting("name").contains("deploy-team");
  }

  @Test
  @DisplayName("resolver throws — falls back to the asserted roles instead of dropping them")
  void resolverFailureFallsBackToAssertedRoles() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenThrow(new RuntimeException("ldap down"));
    GateIdentityService svc = new GateIdentityService(userRolesResolver, null, props());

    Set<String> roles = svc.resolveAndCacheRoles(USER, List.of("deploy-team"));

    assertThat(roles).containsExactly("deploy-team");
  }

  // ---------------------------------------------------------------------------
  // Caching (short-TTL per-principal role cache)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("rolesFor serves the cached resolution and does not re-hit the provider")
  void rolesForServesFromCache() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenReturn(Set.of("deploy-team"));
    GateIdentityService svc = new GateIdentityService(userRolesResolver, null, props());

    svc.resolveAndCacheRoles(USER, List.of("deploy-team"));
    Set<String> cached = svc.rolesFor(USER);

    assertThat(cached).containsExactly("deploy-team");
    // resolveAndCacheRoles resolved once; rolesFor hit the cache (no second provider call).
    verify(userRolesResolver, times(1)).resolveRoleNames(any(ExternalUser.class));
  }

  @Test
  @DisplayName("rolesFor on a cache miss resolves (provider-only) and caches the result")
  void rolesForResolvesOnMiss() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenReturn(Set.of("deploy-team"));
    GateIdentityService svc = new GateIdentityService(userRolesResolver, null, props());

    Set<String> roles = svc.rolesFor(USER);

    assertThat(roles).containsExactly("deploy-team");
    verify(userRolesResolver, times(1)).resolveRoleNames(any(ExternalUser.class));
  }

  @Test
  @DisplayName(
      "EXTERNAL: an empty resolution is not cached, so a later call carrying the principal's roles"
          + " can re-seed instead of being stuck empty")
  void externalDoesNotCacheEmptyAndCanReseed() {
    GateIdentityService svc = new GateIdentityService(null, null, props());

    // Cold/aged-out lookup with no principal roles available: returns empty, but must NOT poison
    // the cache with an empty entry.
    assertThat(svc.rolesFor(USER)).isEmpty();
    assertThat(svc.resolveAndCacheRoles(USER, List.of())).isEmpty();

    // A subsequent request that does carry the principal's asserted roles re-seeds the cache.
    assertThat(svc.rolesFor(USER, List.of("deploy-team"))).containsExactly("deploy-team");
    // ...and is now served from the cache even without re-supplying the roles.
    assertThat(svc.rolesFor(USER)).containsExactly("deploy-team");
  }

  @Test
  @DisplayName(
      "EXTERNAL: rolesFor uses the supplied asserted-fallback roles on a miss (lower-cased)")
  void externalRolesForUsesAssertedFallback() {
    GateIdentityService svc = new GateIdentityService(null, null, props());

    assertThat(svc.rolesFor(USER, List.of("Deploy-Team", "OPS")))
        .containsExactlyInAnyOrder("deploy-team", "ops");
  }

  @Test
  @DisplayName("provider mode: a cache hit ignores the asserted-fallback roles")
  void providerCacheHitIgnoresFallback() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenReturn(Set.of("deploy-team"));
    GateIdentityService svc = new GateIdentityService(userRolesResolver, null, props());

    svc.resolveAndCacheRoles(USER, List.of("deploy-team"));
    Set<String> cached = svc.rolesFor(USER, List.of("late-role"));

    assertThat(cached).containsExactly("deploy-team");
    verify(userRolesResolver, times(1)).resolveRoleNames(any(ExternalUser.class));
  }

  @Test
  @DisplayName("invalidate drops the cached roles, forcing a fresh resolution")
  void invalidateDropsCache() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenReturn(Set.of("deploy-team"));
    GateIdentityService svc = new GateIdentityService(userRolesResolver, null, props());

    svc.rolesFor(USER);
    svc.invalidate(USER);
    svc.rolesFor(USER);

    verify(userRolesResolver, times(2)).resolveRoleNames(any(ExternalUser.class));
  }

  @Test
  @DisplayName("rolesFor(Supplier): the fallback supplier is consulted only on a cache miss")
  void rolesForSupplierIsLazy() {
    GateIdentityService svc = new GateIdentityService(null, null, props());
    AtomicInteger calls = new AtomicInteger();
    Supplier<List<String>> supplier =
        () -> {
          calls.incrementAndGet();
          return List.of("deploy-team");
        };

    // Cold cache: the supplier is consulted and its roles are resolved + cached.
    assertThat(svc.rolesFor("ci-bot", supplier)).containsExactly("deploy-team");
    assertThat(calls.get()).isEqualTo(1);

    // Warm cache: the (expensive) supplier is not consulted again.
    assertThat(svc.rolesFor("ci-bot", supplier)).containsExactly("deploy-team");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("hasUserRolesResolver reflects whether a role provider is configured")
  void hasUserRolesResolverReflectsConfig() {
    assertThat(new GateIdentityService(null, null, props()).hasUserRolesResolver()).isFalse();
    assertThat(new GateIdentityService(userRolesResolver, null, props()).hasUserRolesResolver())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // admin / account-manager derivation (case-insensitive)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("isAdmin / isAccountManager match the configured role mappings, case-insensitively")
  void adminAndAccountManagerDerivation() {
    GateIdentityService svc = new GateIdentityService(null, null, props());

    assertThat(svc.isAdmin(List.of("SPINNAKER-ADMINS"))).isTrue();
    assertThat(svc.isAdmin(List.of("deploy-team"))).isFalse();
    assertThat(svc.isAccountManager(List.of("Account-Managers"))).isTrue();
    assertThat(svc.isAccountManager(List.of("deploy-team"))).isFalse();
  }

  // ---------------------------------------------------------------------------
  // Token minting
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("mintToken returns null when no minter (signing key) is configured")
  void mintTokenNullWithoutMinter() {
    GateIdentityService svc = new GateIdentityService(null, null, props());

    assertThat(svc.mintToken(USER, List.of("deploy-team"))).isNull();
    assertThat(svc.isMinterAvailable()).isFalse();
  }

  @Test
  @DisplayName("mintToken signs the subject + roles and derives the admin claim from role mappings")
  void mintTokenDerivesAdminClaim() {
    when(tokenMinter.mint(anyString(), anyList(), anyBoolean(), anyBoolean()))
        .thenReturn("signed.jwt");
    GateIdentityService svc = new GateIdentityService(null, tokenMinter, props());

    String token = svc.mintToken(USER, List.of("spinnaker-admins", "deploy-team"));

    assertThat(token).isEqualTo("signed.jwt");
    assertThat(svc.isMinterAvailable()).isTrue();
    verify(tokenMinter).mint(eq(USER), anyList(), eq(true), eq(false));
  }

  @Test
  @DisplayName("resolveAndMint resolves+caches the roles and mints in one call")
  void resolveAndMintResolvesAndMints() {
    when(userRolesResolver.resolveRoleNames(any(ExternalUser.class)))
        .thenReturn(Set.of("account-managers"));
    when(tokenMinter.mint(anyString(), anyList(), anyBoolean(), anyBoolean()))
        .thenReturn("signed.jwt");
    GateIdentityService svc = new GateIdentityService(userRolesResolver, tokenMinter, props());

    String token = svc.resolveAndMint(USER, List.of());

    assertThat(token).isEqualTo("signed.jwt");
    // account-manager claim derived; subsequent rolesFor is served from cache (resolved once).
    verify(tokenMinter).mint(eq(USER), anyList(), eq(false), eq(true));
    svc.rolesFor(USER);
    verify(userRolesResolver, times(1)).resolveRoleNames(any(ExternalUser.class));
  }
}
