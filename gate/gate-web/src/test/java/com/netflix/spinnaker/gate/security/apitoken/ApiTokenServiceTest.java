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

package com.netflix.spinnaker.gate.security.apitoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.exceptions.JedisException;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceTest {

  @Mock RedisApiTokenRepository redisRepo;
  @Mock PermissionService permissionService;

  ApiTokenProperties properties;
  ApiTokenService service;

  private static final String HASH = "abc123def456";
  private static final String TOKEN_ID = "token-uuid-1";
  private static final String PRINCIPAL = "alice@example.com";
  private static final String FUTURE_EXPIRY = Instant.now().plus(30, ChronoUnit.DAYS).toString();

  @BeforeEach
  void setUp() {
    properties = new ApiTokenProperties();
    properties.setEnabled(true);
    properties.setRejectIfNoPrincipalPermissions(false);
    properties.setRejectCheckIntervalSeconds(60);
    service = new ApiTokenService(redisRepo, permissionService, properties);
  }

  private TokenRecord makeRecord(String id, String principalId, String expiresAt) {
    TokenRecord r = new TokenRecord();
    r.setId(id);
    r.setPrincipalId(principalId);
    r.setPrincipalType("USER");
    r.setExpiresAt(expiresAt);
    r.setHashRef(HASH);
    return r;
  }

  @Nested
  @DisplayName("resolveByHash — Redis lookup")
  class RedisLookup {

    @Test
    @DisplayName(
        "hash present in Redis returns populated record (null expiresAt round-trips for"
            + " non-expiring SA tokens)")
    void presentHashReturnsRecord() {
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, null)));

      Optional<TokenRecord> result = service.resolveByHash(HASH);

      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(TOKEN_ID);
      assertThat(result.get().getPrincipalId()).isEqualTo(PRINCIPAL);
      assertThat(result.get().getExpiresAt()).isNull();
      verify(redisRepo, times(1)).findByHash(HASH);
    }

    @Test
    @DisplayName("unknown hash returns empty optional")
    void unknownHashReturnsEmpty() {
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.empty());

      assertThat(service.resolveByHash(HASH)).isEmpty();
    }

    @Test
    @DisplayName("skips permission check when rejectIfNoPrincipalPermissions is false")
    void skipsAuthCheckWhenFlagOff() {
      // Lives here rather than under RejectIfNoPermissions because that nested class's @BeforeEach
      // flips the flag on; keep all flag-off behaviour under the top-level setup.
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService, never()).getRolesForTokenAuth(any());
    }
  }

  @Nested
  @DisplayName("resolveByHash with rejectIfNoPrincipalPermissions=true")
  class RejectIfNoPermissions {

    @BeforeEach
    void enableCheck() {
      properties.setRejectIfNoPrincipalPermissions(true);
    }

    @Test
    @DisplayName("resolves token when principal still has permissions")
    void resolvesWhenPrincipalActive() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of("ops"));

      assertThat(service.resolveByHash(HASH)).isPresent();
    }

    @Test
    @DisplayName("returns empty when principal has been offboarded (empty roles)")
    void rejectsWhenPrincipalDeparted() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of());

      assertThat(service.resolveByHash(HASH)).isEmpty();
    }

    @Test
    @DisplayName("returns empty when permission lookup returns 404 for principal")
    void rejectsWhenPrincipalDeleted() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      SpinnakerHttpException notFound = mock(SpinnakerHttpException.class);
      when(notFound.getResponseCode()).thenReturn(404);
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenThrow(notFound);

      assertThat(service.resolveByHash(HASH)).isEmpty();
    }

    @Test
    @DisplayName("fails open when permission lookup is temporarily unreachable")
    void failsOpenWhenPermissionLookupDown() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL))
          .thenThrow(new RuntimeException("timeout"));

      assertThat(service.resolveByHash(HASH)).isPresent();
    }

    @Test
    @DisplayName(
        "fails open when permission lookup returns 503 — repository purged, not a departed user")
    void failsOpenWhenPermissionLookupReturns503() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      SpinnakerHttpException repoEmpty = mock(SpinnakerHttpException.class);
      when(repoEmpty.getResponseCode()).thenReturn(503);
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenThrow(repoEmpty);

      assertThat(service.resolveByHash(HASH)).isPresent();
    }

    @Test
    @DisplayName("resolves and does not check permissions when PermissionService is disabled")
    void resolvesWhenPermissionServiceDisabled() {
      when(permissionService.isEnabled()).thenReturn(false);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService, never()).getRolesForTokenAuth(any());
    }

    @Test
    @DisplayName("skips permission check when lastAuthCheckAt is within the interval")
    void skipsAuthCheckWhenWithinInterval() {
      properties.setRejectCheckIntervalSeconds(60);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setLastAuthCheckAt(Instant.now().minusSeconds(30).toString());
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService, never()).getRolesForTokenAuth(any());
    }

    @Test
    @DisplayName("triggers permission check when lastAuthCheckAt has passed the interval")
    void triggersAuthCheckWhenIntervalElapsed() {
      when(permissionService.isEnabled()).thenReturn(true);
      properties.setRejectCheckIntervalSeconds(60);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setLastAuthCheckAt(Instant.now().minusSeconds(120).toString());
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of("ops"));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService).getRolesForTokenAuth(PRINCIPAL);
      verify(redisRepo).updateLastAuthCheck(eq(TOKEN_ID), eq(HASH), any(Instant.class));
    }

    @Test
    @DisplayName("triggers permission check when lastAuthCheckAt is absent")
    void triggersAuthCheckWhenLastCheckAbsent() {
      when(permissionService.isEnabled()).thenReturn(true);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of("ops"));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService).getRolesForTokenAuth(PRINCIPAL);
    }

    @Test
    @DisplayName(
        "a failed auth-check timestamp write does not fail authentication — advancing the throttle"
            + " is best-effort, and dropping it only means the next request re-checks")
    void timestampWriteFailureDoesNotFailAuthentication() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of("ops"));
      doThrow(new JedisException("connection reset"))
          .when(redisRepo)
          .updateLastAuthCheck(eq(TOKEN_ID), eq(HASH), any(Instant.class));

      assertThat(service.resolveByHash(HASH)).isPresent();
    }
  }

  @Nested
  @DisplayName(
      "rejectIfNoPrincipalPermissions — resolution must mirror ApiTokenAuthenticationFilter, so the"
          + " check can never reject a token the filter would have authenticated")
  class RejectCheckMirrorsFilterResolution {

    private static final String SERVICE_ACCOUNT = "svc_gitops@example.com";

    @BeforeEach
    void enableCheck() {
      properties.setRejectIfNoPrincipalPermissions(true);
      when(permissionService.isEnabled()).thenReturn(true);
    }

    private TokenRecord serviceAccountRecord() {
      TokenRecord r = makeRecord(TOKEN_ID, SERVICE_ACCOUNT, FUTURE_EXPIRY);
      r.setPrincipalType("SERVICE_ACCOUNT");
      return r;
    }

    @Test
    @DisplayName(
        "service-account principal resolves from the SA's Front50 memberOf, not the user role"
            + " provider — which never knows a service account and would reject every SA token")
    void serviceAccountResolvesFromMemberOf() {
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(serviceAccountRecord()));
      when(permissionService.resolveServiceAccountRoles(SERVICE_ACCOUNT))
          .thenReturn(java.util.Set.of("spin-internal-service-accounts"));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService).resolveServiceAccountRoles(SERVICE_ACCOUNT);
      verify(permissionService, never()).getRolesForTokenAuth(any());
    }

    @Test
    @DisplayName("service account with no memberOf roles is still rejected")
    void serviceAccountWithoutMemberOfIsRejected() {
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(serviceAccountRecord()));
      when(permissionService.resolveServiceAccountRoles(SERVICE_ACCOUNT))
          .thenReturn(java.util.Set.of());

      assertThat(service.resolveByHash(HASH)).isEmpty();
    }

    @Test
    @DisplayName(
        "with no role provider configured, the roles snapshotted on the token at creation keep it"
            + " alive — live resolution can never succeed in that mode")
    void snapshotHonouredWhenNoRoleProvider() {
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setRoles(List.of("ops"));
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of());
      when(permissionService.hasUserRolesResolver()).thenReturn(false);

      assertThat(service.resolveByHash(HASH)).isPresent();
    }

    @Test
    @DisplayName(
        "with a role provider configured, a stale snapshot does NOT keep a deprovisioned user alive")
    void snapshotIgnoredWhenRoleProviderConfigured() {
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setRoles(List.of("ops"));
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenReturn(java.util.Set.of());
      when(permissionService.hasUserRolesResolver()).thenReturn(true);

      assertThat(service.resolveByHash(HASH)).isEmpty();
    }
  }

  @Nested
  @DisplayName("canMintApiTokens — single source of truth for minting policy")
  class CanMintApiTokens {

    @Test
    @DisplayName("returns false when the API token subsystem is disabled")
    void returnsFalseWhenDisabled() {
      properties.setEnabled(false);

      assertThat(service.canMintApiTokens(PRINCIPAL)).isFalse();
      verify(permissionService, never()).isAdmin(any());
      verify(permissionService, never()).getRoles(any());
    }

    @Test
    @DisplayName("returns true for admins regardless of allowedMintingRoles")
    void returnsTrueForAdmin() {
      properties.setAllowedMintingRoles(List.of());
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(true);

      assertThat(service.canMintApiTokens(PRINCIPAL)).isTrue();
      verify(permissionService, never()).getRoles(any());
    }

    @Test
    @DisplayName("returns false for non-admin when allowedMintingRoles is empty")
    void returnsFalseWhenNoMintingRolesConfigured() {
      properties.setAllowedMintingRoles(List.of());
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(false);

      assertThat(service.canMintApiTokens(PRINCIPAL)).isFalse();
      verify(permissionService, never()).getRoles(any());
    }

    @Test
    @DisplayName("returns true when a user role intersects allowedMintingRoles")
    void returnsTrueWhenUserHasAllowedRole() {
      properties.setAllowedMintingRoles(List.of("api-minters", "platform"));
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(false);
      when(permissionService.getRoles(PRINCIPAL))
          .thenReturn(java.util.Set.of("api-minters", "read-only"));

      assertThat(service.canMintApiTokens(PRINCIPAL)).isTrue();
    }

    @Test
    @DisplayName("returns false when no user role intersects allowedMintingRoles")
    void returnsFalseWhenUserHasNoAllowedRole() {
      properties.setAllowedMintingRoles(List.of("api-minters"));
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(false);
      when(permissionService.getRoles(PRINCIPAL)).thenReturn(java.util.Set.of("read-only"));

      assertThat(service.canMintApiTokens(PRINCIPAL)).isFalse();
    }

    @Test
    @DisplayName("returns false when getRoles returns an empty set")
    void returnsFalseWhenUserHasNoRoles() {
      properties.setAllowedMintingRoles(List.of("api-minters"));
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(false);
      when(permissionService.getRoles(PRINCIPAL)).thenReturn(java.util.Set.of());

      assertThat(service.canMintApiTokens(PRINCIPAL)).isFalse();
    }

    @Test
    @DisplayName("returns false when getRoles returns null")
    void returnsFalseWhenGetRolesReturnsNull() {
      properties.setAllowedMintingRoles(List.of("api-minters"));
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(false);
      when(permissionService.getRoles(PRINCIPAL)).thenReturn(null);

      assertThat(service.canMintApiTokens(PRINCIPAL)).isFalse();
    }
  }
}
