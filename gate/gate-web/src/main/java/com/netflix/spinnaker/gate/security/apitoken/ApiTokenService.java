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

package com.netflix.spinnaker.gate.security.apitoken;

import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves API token hashes to {@link TokenRecord}s, using Redis as the authoritative store
 * (revocation is therefore instantly visible across all Gate replicas).
 *
 * <p>When {@code api-tokens.reject-if-no-principal-permissions} is {@code true}, the local {@link
 * PermissionService} is consulted at most once per {@code reject-check-interval-seconds} per token;
 * the last-check timestamp is stored on the token record so the throttle is shared across replicas.
 */
@Slf4j
public class ApiTokenService {

  private final RedisApiTokenRepository redisRepo;
  private final PermissionService permissionService;
  private final ApiTokenProperties properties;

  /**
   * Fire-and-forget executor for {@code lastUsedAt} updates so the auth hot path is never blocked
   * on a Redis write. Instance-scoped so {@link #shutdownAsyncPool()} drains it on context refresh.
   */
  private final ExecutorService asyncPool =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "api-token-lastused-updater");
            t.setDaemon(true);
            return t;
          });

  public ApiTokenService(
      RedisApiTokenRepository redisRepo,
      PermissionService permissionService,
      ApiTokenProperties properties) {
    this.redisRepo = redisRepo;
    this.permissionService = permissionService;
    this.properties = properties;
  }

  /**
   * Resolve a SHA-256 hex hash to a {@link TokenRecord}. Returns empty if the hash is unknown in
   * Redis, or if the principal no longer has any permissions (when {@code
   * reject-if-no-principal-permissions} is enabled and the check interval has elapsed).
   *
   * @param tokenHash SHA-256 hex of the raw token value
   */
  public Optional<TokenRecord> resolveByHash(String tokenHash) {
    Optional<TokenRecord> opt = redisRepo.findByHash(tokenHash);
    if (opt.isEmpty()) {
      return Optional.empty();
    }

    TokenRecord record = opt.get();

    if (properties.isRejectIfNoPrincipalPermissions()) {
      boolean checkNeeded = isAuthCheckNeeded(record);
      if (checkNeeded) {
        if (!principalHasPermissions(record)) {
          log.info(
              "Rejecting API token id={} — principal '{}' has no permissions",
              record.getId(),
              record.getPrincipalId());
          return Optional.empty();
        }
        Instant now = Instant.now();
        // Best-effort: this only advances the re-check throttle. Losing the write means the next
        // request re-checks, so it must never fail authentication — including on a Redis blip.
        try {
          redisRepo.updateLastAuthCheck(record.getId(), tokenHash, now);
        } catch (RuntimeException e) {
          log.debug("Could not advance the auth-check timestamp; request proceeds", e);
        }
        record.setLastAuthCheckAt(now.toString());
      }
    }

    return opt;
  }

  /**
   * Returns {@code true} if the principal has at least one permission, or if the {@link
   * PermissionService} is disabled.
   *
   * <p>Role resolution mirrors {@link ApiTokenAuthenticationFilter} — see {@link
   * #resolveRolesForCheck}. Consulting only the user role provider would reject every
   * service-account token, and every token in a deployment with no role provider at all.
   *
   * <ul>
   *   <li><b>404</b> — user definitively absent from the permission store; returns {@code false}.
   *   <li><b>Any other error</b> — permission lookup temporarily unavailable; fails open, returns
   *       {@code true}.
   * </ul>
   */
  public boolean principalHasPermissions(TokenRecord record) {
    if (!permissionService.isEnabled()) {
      return true;
    }
    String principalId = record.getPrincipalId();
    try {
      return !resolveRolesForCheck(record).isEmpty();
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == 404) {
        log.info("Principal '{}' not found (404) — treating as departed", principalId);
        return false;
      }
      log.warn(
          "Permission lookup returned HTTP {} for principal '{}' — failing open: {}",
          e.getResponseCode(),
          principalId,
          e.getMessage());
      return true;
    } catch (Exception e) {
      log.warn(
          "Could not resolve permissions for principal '{}' — failing open: {}",
          principalId,
          e.getMessage());
      return true;
    }
  }

  /**
   * The roles the deprovisioning check treats as evidence the principal is still live. Kept in step
   * with {@link ApiTokenAuthenticationFilter}'s resolution so the check can never reject a token
   * the filter would have authenticated:
   *
   * <ul>
   *   <li>service accounts resolve from the SA's Front50 {@code memberOf} — the user role provider
   *       never knows a service account;
   *   <li>with no role provider configured, roles can't be re-derived from a principal id (there is
   *       no live login session here), so the snapshot captured on the token at creation is
   *       authoritative. Gated on there being no provider so provider-backed deployments never keep
   *       a deprovisioned user alive off a stale snapshot.
   * </ul>
   */
  private Set<String> resolveRolesForCheck(TokenRecord record) {
    String principalId = record.getPrincipalId();
    if ("SERVICE_ACCOUNT".equalsIgnoreCase(record.getPrincipalType())) {
      return permissionService.resolveServiceAccountRoles(principalId);
    }
    Set<String> roles = permissionService.getRolesForTokenAuth(principalId);
    if ((roles == null || roles.isEmpty()) && !permissionService.hasUserRolesResolver()) {
      List<String> snapshot = record.getRoles();
      if (snapshot != null && !snapshot.isEmpty()) {
        return new LinkedHashSet<>(snapshot);
      }
    }
    return roles == null ? Set.of() : roles;
  }

  /**
   * Single source of truth for "may this user mint API tokens?" — used by both {@code
   * AuthController.user} (UI flag) and {@code ApiTokenController} (POST enforcement) so the two
   * views can't drift.
   *
   * <ol>
   *   <li>Subsystem disabled → no.
   *   <li>Admin → yes.
   *   <li>No minting roles configured → no (for non-admins).
   *   <li>Otherwise the user must hold at least one configured minting role.
   * </ol>
   */
  public boolean canMintApiTokens(String username) {
    if (!properties.isEnabled()) {
      return false;
    }
    if (permissionService.isAdmin(username)) {
      return true;
    }
    List<String> allowed = properties.getAllowedMintingRoles();
    if (allowed == null || allowed.isEmpty()) {
      return false;
    }
    Collection<String> userRoles = permissionService.getRoles(username);
    if (userRoles == null || userRoles.isEmpty()) {
      return false;
    }
    return userRoles.stream().anyMatch(allowed::contains);
  }

  /**
   * Asynchronously update {@code lastUsedAt}. Best-effort; failures are swallowed. The task
   * propagates the originating request's MDC so async logs carry the same context.
   */
  public void touchLastUsedAsync(String tokenId, String sha256Hex) {
    asyncPool.submit(
        AuthenticatedRequest.propagate(
            () -> {
              try {
                redisRepo.updateLastUsed(tokenId, sha256Hex, Instant.now());
              } catch (Exception e) {
                log.debug("Failed to update lastUsedAt for token {}: {}", tokenId, e.getMessage());
              }
              return null;
            }));
  }

  /** Drain {@link #asyncPool} on bean destruction; best-effort with a short timeout. */
  @PreDestroy
  void shutdownAsyncPool() {
    asyncPool.shutdown();
    try {
      if (!asyncPool.awaitTermination(2, TimeUnit.SECONDS)) {
        log.warn("api-token-lastused-updater did not terminate in 2s; forcing shutdownNow()");
        asyncPool.shutdownNow();
      }
    } catch (InterruptedException e) {
      asyncPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private boolean isAuthCheckNeeded(TokenRecord record) {
    String lastCheck = record.getLastAuthCheckAt();
    if (lastCheck == null || lastCheck.isBlank()) {
      return true;
    }
    try {
      Instant last = Instant.parse(lastCheck);
      return Instant.now().isAfter(last.plusSeconds(properties.getRejectCheckIntervalSeconds()));
    } catch (Exception e) {
      return true;
    }
  }
}
