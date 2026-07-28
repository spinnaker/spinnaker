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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.netflix.spinnaker.security.authz.Role;
import com.netflix.spinnaker.security.roles.ExternalUser;
import com.netflix.spinnaker.security.roles.UserRolesResolver;
import com.netflix.spinnaker.security.token.SpinnakerTokenMinter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Edge identity facade: the single place where Gate resolves a caller's roles (via the {@link
 * UserRolesResolver} from {@code kork-roles}) and mints the signed identity token (via {@link
 * SpinnakerTokenMinter} from {@code kork-security}).
 *
 * <p>Roles are resolved locally on the login paths. It is invoked by:
 *
 * <ul>
 *   <li>every interactive login mechanism (OAuth2/OIDC, SAML, LDAP, x509, header, IAP, basic) via
 *       {@code PermissionService.login}/{@code loginWithRoles} and {@code AuthenticationService};
 *   <li>the per-request {@link IdentityTokenPropagationFilter} which re-mints the short-lived token
 *       on each downstream call;
 *   <li>the {@code spk_} API-token filter, which performs an edge token-exchange (resolve roles +
 *       mint) at request time.
 * </ul>
 *
 * <p>A short-TTL per-principal role cache absorbs high-volume traffic (notably CI using API tokens)
 * so the underlying providers are not consulted on every request. When no {@link UserRolesResolver}
 * bean is configured (no role provider), resolution falls back to trusting the roles asserted by
 * the authentication mechanism.
 */
public class GateIdentityService {

  private static final Logger log = LoggerFactory.getLogger(GateIdentityService.class);

  @Nullable private final UserRolesResolver userRolesResolver;
  @Nullable private final SpinnakerTokenMinter tokenMinter;
  private final Set<String> adminRoles;
  private final Set<String> accountManagerRoles;
  private final Cache<String, Set<String>> roleCache;

  public GateIdentityService(
      @Nullable UserRolesResolver userRolesResolver,
      @Nullable SpinnakerTokenMinter tokenMinter,
      GateAuthzProperties properties) {
    this.userRolesResolver = userRolesResolver;
    this.tokenMinter = tokenMinter;
    this.adminRoles = lowerCaseSet(properties.getAdminRoles());
    this.accountManagerRoles = lowerCaseSet(properties.getAccountManagerRoles());
    this.roleCache =
        Caffeine.newBuilder()
            .maximumSize(properties.getRoleCacheMaximumSize())
            .expireAfterAccess(properties.getRoleCacheTtl())
            .build();
    if (userRolesResolver == null) {
      log.info(
          "No UserRolesResolver configured; Gate will trust roles asserted by the authentication "
              + "mechanism (set auth.group-membership.service to enable a role provider).");
    }
    if (tokenMinter == null) {
      log.warn("No SpinnakerTokenMinter configured; identity tokens will not be minted.");
    }
  }

  /**
   * Resolve (provider + external-group merge) the roles for the given user, given the roles already
   * asserted by the authentication mechanism, and cache the result. This is the login-time entry
   * point.
   */
  public Set<String> resolveAndCacheRoles(
      String userId, @Nullable Collection<String> assertedRoles) {
    Set<String> resolved = resolve(userId, assertedRoles);
    // With a provider configured, roles are reconstructable from the userId alone, so caching the
    // result (even an empty one) is safe — a later miss simply re-resolves. With no provider
    // (EXTERNAL), roles only ever come from the live authentication and can never be re-derived
    // from a userId. Caching an empty set there would poison the cache: a subsequent miss without
    // the principal (e.g. an aged-out entry, or an admin/role lookup off the request thread) could
    // re-cache empty even though the session principal still carries roles. Only persist a
    // non-empty result in that mode so the next call carrying the principal can re-seed it.
    if (userRolesResolver != null || !resolved.isEmpty()) {
      roleCache.put(userId, resolved);
    }
    return resolved;
  }

  /**
   * Return the cached roles for the user, resolving (provider-only, no freshly-asserted roles) on a
   * cache miss. Used by callers that have no access to the live authentication; prefer {@link
   * #rolesFor(String, Collection)} on the request path so EXTERNAL roles can be re-seeded from the
   * principal.
   */
  public Set<String> rolesFor(String userId) {
    return rolesFor(userId, (Collection<String>) null);
  }

  /**
   * Return the cached roles for the user, resolving on a cache miss. On a miss the supplied {@code
   * assertedFallbackRoles} (the roles carried by the live authentication/session principal) are fed
   * into resolution: with a provider they are merged with the freshly-resolved provider roles
   * (matching login behaviour, preserving TTL-based refresh); with no provider (EXTERNAL) they are
   * the authoritative source, letting an aged-out cache entry be rebuilt from the principal rather
   * than collapsing to empty. Used by the per-request token-minting path.
   */
  public Set<String> rolesFor(String userId, @Nullable Collection<String> assertedFallbackRoles) {
    Set<String> cached = roleCache.getIfPresent(userId);
    if (cached != null) {
      return cached;
    }
    return resolveAndCacheRoles(userId, assertedFallbackRoles);
  }

  /**
   * Like {@link #rolesFor(String, Collection)}, but the fallback roles are produced lazily and only
   * consulted on a cache miss. Use when computing the fallback is expensive (e.g. a remote lookup
   * such as a service account's Front50 {@code memberOf}) so it's skipped entirely on the hot
   * cache-hit path.
   */
  public Set<String> rolesFor(
      String userId, @Nullable Supplier<? extends Collection<String>> assertedFallbackSupplier) {
    Set<String> cached = roleCache.getIfPresent(userId);
    if (cached != null) {
      return cached;
    }
    Collection<String> asserted =
        assertedFallbackSupplier == null ? null : assertedFallbackSupplier.get();
    return resolveAndCacheRoles(userId, asserted);
  }

  private Set<String> resolve(String userId, @Nullable Collection<String> assertedRoles) {
    Set<String> asserted = lowerCaseSet(assertedRoles);
    if (userRolesResolver == null) {
      return asserted;
    }
    ExternalUser externalUser =
        new ExternalUser()
            .setId(userId)
            .setExternalRoles(
                asserted.stream()
                    .map(name -> new Role(name).setSource(Role.Source.EXTERNAL))
                    .collect(Collectors.toList()));
    try {
      return new LinkedHashSet<>(userRolesResolver.resolveRoleNames(externalUser));
    } catch (RuntimeException e) {
      // Don't let a transient provider failure drop a caller's asserted roles entirely.
      log.warn("Role resolution failed for '{}'; falling back to asserted roles", userId, e);
      return asserted;
    }
  }

  /** True when the supplied roles grant Spinnaker admin per {@code authz.gate.admin-roles}. */
  public boolean isAdmin(Collection<String> roles) {
    return intersects(roles, adminRoles);
  }

  /**
   * True when the supplied roles grant account-manager status per {@code
   * authz.gate.account-manager-roles}.
   */
  public boolean isAccountManager(Collection<String> roles) {
    return intersects(roles, accountManagerRoles);
  }

  /** Whether a signing key is configured so tokens can be minted. */
  public boolean isMinterAvailable() {
    return tokenMinter != null;
  }

  /**
   * Whether a role provider is configured. When {@code false} (the {@code EXTERNAL}
   * group-membership mode), roles can only come from the live authentication and cannot be
   * re-resolved from a principal id alone — callers without a live principal (e.g. API-token
   * exchange) must supply their own role source.
   */
  public boolean hasUserRolesResolver() {
    return userRolesResolver != null;
  }

  /**
   * Mint a signed identity token for the supplied subject and roles, deriving the admin /
   * account-manager claims from the configured role mappings. Returns {@code null} when no minter
   * is configured.
   */
  @Nullable
  public String mintToken(String subject, Collection<String> roles) {
    if (tokenMinter == null) {
      return null;
    }
    List<String> roleList = roles == null ? List.of() : List.copyOf(new LinkedHashSet<>(roles));
    return tokenMinter.mint(subject, roleList, isAdmin(roleList), isAccountManager(roleList));
  }

  /** Convenience: resolve+cache the roles, then mint a token for the user. */
  @Nullable
  public String resolveAndMint(String userId, @Nullable Collection<String> assertedRoles) {
    return mintToken(userId, resolveAndCacheRoles(userId, assertedRoles));
  }

  /** Drop any cached roles for the user (e.g. on logout). */
  public void invalidate(String userId) {
    if (userId != null) {
      roleCache.invalidate(userId);
    }
  }

  private static boolean intersects(Collection<String> roles, Set<String> against) {
    if (roles == null || roles.isEmpty() || against.isEmpty()) {
      return false;
    }
    return roles.stream()
        .filter(Objects::nonNull)
        .map(r -> r.toLowerCase(Locale.ROOT))
        .anyMatch(against::contains);
  }

  private static Set<String> lowerCaseSet(@Nullable Collection<String> values) {
    if (values == null) {
      return new LinkedHashSet<>();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .map(v -> v.toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
