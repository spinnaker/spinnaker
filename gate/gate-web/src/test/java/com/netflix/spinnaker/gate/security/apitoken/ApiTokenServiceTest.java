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

import com.netflix.spinnaker.fiat.model.resources.Role.View;
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
    @DisplayName("skips Fiat check when rejectIfNoPrincipalPermissions is false")
    void skipsFiatCheckWhenFlagOff() {
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
    @DisplayName("resolves token when principal is still active in Fiat")
    void resolvesWhenPrincipalActive() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      View roleView = new View();
      roleView.setName("ops");
      when(permissionService.getRolesForTokenAuth(PRINCIPAL))
          .thenReturn(java.util.Set.of(roleView));

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
    @DisplayName("returns empty when Fiat returns 404 for principal")
    void rejectsWhenPrincipalDeletedFromFiat() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      SpinnakerHttpException notFound = mock(SpinnakerHttpException.class);
      when(notFound.getResponseCode()).thenReturn(404);
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenThrow(notFound);

      assertThat(service.resolveByHash(HASH)).isEmpty();
    }

    @Test
    @DisplayName("fails open when Fiat is temporarily unreachable")
    void failsOpenWhenFiatDown() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      when(permissionService.getRolesForTokenAuth(PRINCIPAL))
          .thenThrow(new RuntimeException("timeout"));

      assertThat(service.resolveByHash(HASH)).isPresent();
    }

    @Test
    @DisplayName("fails open when Fiat returns 503 — repository purged, not a departed user")
    void failsOpenWhenFiatReturns503() {
      when(permissionService.isEnabled()).thenReturn(true);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));
      SpinnakerHttpException repoEmpty = mock(SpinnakerHttpException.class);
      when(repoEmpty.getResponseCode()).thenReturn(503);
      when(permissionService.getRolesForTokenAuth(PRINCIPAL)).thenThrow(repoEmpty);

      assertThat(service.resolveByHash(HASH)).isPresent();
    }

    @Test
    @DisplayName("resolves and does not call Fiat when Fiat is disabled")
    void resolvesWhenFiatDisabled() {
      when(permissionService.isEnabled()).thenReturn(false);
      when(redisRepo.findByHash(HASH))
          .thenReturn(Optional.of(makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY)));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService, never()).getRolesForTokenAuth(any());
    }

    @Test
    @DisplayName("skips Fiat check when lastFiatCheckAt is within the interval")
    void skipsFiatCheckWhenWithinInterval() {
      properties.setRejectCheckIntervalSeconds(60);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setLastFiatCheckAt(Instant.now().minusSeconds(30).toString());
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService, never()).getRolesForTokenAuth(any());
    }

    @Test
    @DisplayName("triggers Fiat check when lastFiatCheckAt has passed the interval")
    void triggersFiatCheckWhenIntervalElapsed() {
      when(permissionService.isEnabled()).thenReturn(true);
      properties.setRejectCheckIntervalSeconds(60);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setLastFiatCheckAt(Instant.now().minusSeconds(120).toString());
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      View roleView = new View();
      roleView.setName("ops");
      when(permissionService.getRolesForTokenAuth(PRINCIPAL))
          .thenReturn(java.util.Set.of(roleView));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService).getRolesForTokenAuth(PRINCIPAL);
      verify(redisRepo).updateLastFiatCheck(eq(TOKEN_ID), eq(HASH), any(Instant.class));
    }

    @Test
    @DisplayName(
        "swallows TokenOperationFailedException from updateLastFiatCheck — request still resolves")
    void resolvesWhenLastFiatCheckWriteLosesWatchRace() {
      // Bookkeeping write is best-effort: a lost WATCH race must not 500 a valid request.
      when(permissionService.isEnabled()).thenReturn(true);
      properties.setRejectCheckIntervalSeconds(60);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      record.setLastFiatCheckAt(Instant.now().minusSeconds(120).toString());
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      View roleView = new View();
      roleView.setName("ops");
      when(permissionService.getRolesForTokenAuth(PRINCIPAL))
          .thenReturn(java.util.Set.of(roleView));
      doThrow(new RedisApiTokenRepository.TokenOperationFailedException("lost WATCH race"))
          .when(redisRepo)
          .updateLastFiatCheck(eq(TOKEN_ID), eq(HASH), any(Instant.class));

      Optional<TokenRecord> result = service.resolveByHash(HASH);

      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(TOKEN_ID);
      verify(redisRepo).updateLastFiatCheck(eq(TOKEN_ID), eq(HASH), any(Instant.class));
    }

    @Test
    @DisplayName("triggers Fiat check when lastFiatCheckAt is absent")
    void triggersFiatCheckWhenLastCheckAbsent() {
      when(permissionService.isEnabled()).thenReturn(true);
      TokenRecord record = makeRecord(TOKEN_ID, PRINCIPAL, FUTURE_EXPIRY);
      when(redisRepo.findByHash(HASH)).thenReturn(Optional.of(record));
      View roleView = new View();
      roleView.setName("ops");
      when(permissionService.getRolesForTokenAuth(PRINCIPAL))
          .thenReturn(java.util.Set.of(roleView));

      assertThat(service.resolveByHash(HASH)).isPresent();
      verify(permissionService).getRolesForTokenAuth(PRINCIPAL);
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
    @DisplayName("returns true for Fiat admins regardless of allowedMintingRoles")
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
      View allowedRole = new View();
      allowedRole.setName("api-minters");
      View extraRole = new View();
      extraRole.setName("read-only");
      when(permissionService.getRoles(PRINCIPAL))
          .thenReturn(java.util.Set.of(allowedRole, extraRole));

      assertThat(service.canMintApiTokens(PRINCIPAL)).isTrue();
    }

    @Test
    @DisplayName("returns false when no user role intersects allowedMintingRoles")
    void returnsFalseWhenUserHasNoAllowedRole() {
      properties.setAllowedMintingRoles(List.of("api-minters"));
      when(permissionService.isAdmin(PRINCIPAL)).thenReturn(false);
      View otherRole = new View();
      otherRole.setName("read-only");
      when(permissionService.getRoles(PRINCIPAL)).thenReturn(java.util.Set.of(otherRole));

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
