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

package com.netflix.spinnaker.gate.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.resources.Role.View;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenProperties;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenService;
import com.netflix.spinnaker.gate.security.apitoken.RedisApiTokenRepository;
import com.netflix.spinnaker.gate.security.apitoken.RedisApiTokenRepository.DuplicateTokenNameException;
import com.netflix.spinnaker.gate.security.apitoken.TokenRecord;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.security.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class ApiTokenControllerTest {

  @Mock RedisApiTokenRepository redisRepo;
  @Mock PermissionService permissionService;
  @Mock Front50Service front50Service;

  ApiTokenProperties properties;
  ApiTokenController controller;
  MockMvc mockMvc;
  ObjectMapper objectMapper = new ObjectMapper();

  private static final String USER_EMAIL = "alice@doordash.com";
  private static final String ADMIN_EMAIL = "admin@doordash.com";
  private static final String TOKEN_ID = "tok-uuid-abc";
  private static final String HASH_REF = "sha256-hex-of-token";
  private static final String FUTURE_EXPIRY = Instant.now().plus(30, ChronoUnit.DAYS).toString();

  @BeforeEach
  void setUp() {
    properties = new ApiTokenProperties();
    properties.setEnabled(true);
    properties.setAllowedMintingRoles(List.of("api-minters"));
    properties.setMaxUserTokenLifetimeDays(90);
    properties.setMaxServiceAccountTokenLifetimeDays(365);
    properties.setTokenPrefix("spk_");

    // Real ApiTokenService so the minting-role check is driven by the mocked PermissionService —
    // preserves end-to-end coverage now that the policy lives in canMintApiTokens.
    ApiTokenService apiTokenService = new ApiTokenService(redisRepo, permissionService, properties);
    controller =
        new ApiTokenController(
            redisRepo, permissionService, properties, front50Service, apiTokenService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            // standaloneSetup omits Spring Security's ExceptionTranslationFilter (which maps
            // AccessDeniedException → 403), so we add a tiny ControllerAdvice instead.
            .setControllerAdvice(new AccessDeniedTo403Advice())
            .build();
  }

  @ControllerAdvice
  static class AccessDeniedTo403Advice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handle() {}
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void setCurrentUser(String username) {
    User user = new User();
    user.setUsername(username);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(user, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private TokenRecord makeRecord(
      String id, String principalId, String principalType, String expiresAt) {
    TokenRecord r = new TokenRecord();
    r.setId(id);
    r.setName("test-token");
    r.setPrincipalId(principalId);
    r.setPrincipalType(principalType);
    r.setExpiresAt(expiresAt);
    r.setHashRef(HASH_REF);
    r.setCreatedAt(Instant.now().toString());
    return r;
  }

  // ---------------------------------------------------------------------------
  // GET /auth/apiTokens
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /auth/apiTokens")
  class ListTokens {

    @Test
    @DisplayName("user sees only their own USER tokens from Redis")
    void userSeesOwnTokens() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      when(redisRepo.findByPrincipal("USER", USER_EMAIL))
          .thenReturn(List.of(makeRecord(TOKEN_ID, USER_EMAIL, "USER", FUTURE_EXPIRY)));

      mockMvc
          .perform(get("/auth/apiTokens"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].id").value(TOKEN_ID))
          .andExpect(jsonPath("$[0].hashRef").doesNotExist());
    }

    @Test
    @DisplayName("returns 401 when user is not authenticated")
    void unauthenticatedReturns401() throws Exception {
      mockMvc.perform(get("/auth/apiTokens")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("empty list returned when user has no tokens")
    void emptyListWhenNoTokens() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      when(redisRepo.findByPrincipal("USER", USER_EMAIL)).thenReturn(List.of());

      mockMvc
          .perform(get("/auth/apiTokens"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }
  }

  // ---------------------------------------------------------------------------
  // POST /auth/apiTokens
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("POST /auth/apiTokens")
  class CreateToken {

    @Test
    @DisplayName("user in minting role creates a USER token saved to Redis")
    void mintingRoleUserCreatesToken() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      View roleView = new View();
      roleView.setName("api-minters");
      when(permissionService.getRoles(USER_EMAIL)).thenReturn(java.util.Set.of(roleView));
      when(redisRepo.findByPrincipal("USER", USER_EMAIL)).thenReturn(List.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "my-ci-token"))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.token").exists())
          .andExpect(jsonPath("$.hashRef").doesNotExist());

      verify(redisRepo).save(any(TokenRecord.class), anyString());
    }

    @Test
    @DisplayName("user without minting role is denied — 403")
    void noRoleUserIsDenied() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      when(permissionService.getRoles(USER_EMAIL)).thenReturn(java.util.Set.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "bad-token"))))
          .andExpect(status().isForbidden());

      verify(redisRepo, never()).save(any(), any());
    }

    @Test
    @DisplayName("admin bypasses minting role check")
    void adminBypassesMintingRole() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(redisRepo.findByPrincipal("USER", ADMIN_EMAIL)).thenReturn(List.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "admin-token"))))
          .andExpect(status().isCreated());

      verify(permissionService, never()).getRoles(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("admin creates SERVICE_ACCOUNT token for a valid SA")
    void adminCreatesServiceAccountToken() throws Exception {
      String saName = "ci-pipeline-bot";
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(front50Service.getTokenEligibleServiceAccounts())
          .thenReturn(Calls.response(List.of(Map.of("name", saName))));
      when(redisRepo.findByPrincipal("SERVICE_ACCOUNT", saName)).thenReturn(List.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "name", "ci-token",
                              "principalType", "SERVICE_ACCOUNT",
                              "principalId", saName))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("non-admin cannot create SERVICE_ACCOUNT token — 403")
    void nonAdminCannotCreateSaToken() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "name", "bad",
                              "principalType", "SERVICE_ACCOUNT",
                              "principalId", "ci-bot"))))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("token name is required — 400 if blank")
    void missingNameIsBadRequest() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", ""))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("token name longer than 64 chars is rejected — 400")
    void overlongNameIsBadRequest() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);

      String tooLong = "a".repeat(ApiTokenController.MAX_TOKEN_NAME_LENGTH + 1);
      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", tooLong))))
          .andExpect(status().isBadRequest());

      verify(redisRepo, never()).save(any(), any());
    }

    @Test
    @DisplayName("token name containing control characters is rejected — 400")
    void controlCharsInNameIsBadRequest() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);

      // Embedded newline — would otherwise survive into Redis JSON and log output.
      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "evil\nname"))))
          .andExpect(status().isBadRequest());

      verify(redisRepo, never()).save(any(), any());
    }

    @Test
    @DisplayName("token name is trimmed before persistence")
    void nameIsTrimmedBeforePersistence() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(redisRepo.findByPrincipal("USER", ADMIN_EMAIL)).thenReturn(List.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "  padded  "))))
          .andExpect(status().isCreated());

      ArgumentCaptor<TokenRecord> captor = ArgumentCaptor.forClass(TokenRecord.class);
      verify(redisRepo).save(captor.capture(), anyString());
      assertThat(captor.getValue().getName()).isEqualTo("padded");
    }

    @Test
    @DisplayName("duplicate name for same principal — 409 Conflict")
    void duplicateNameIsConflict() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      TokenRecord existing = makeRecord("existing-id", ADMIN_EMAIL, "USER", FUTURE_EXPIRY);
      existing.setName("duplicate-name");
      when(redisRepo.findByPrincipal("USER", ADMIN_EMAIL)).thenReturn(List.of(existing));

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "duplicate-name"))))
          .andExpect(status().isConflict());
    }

    @Test
    @DisplayName(
        "save throws DuplicateTokenNameException after pre-flight check passes — 409 Conflict")
    void raceLostInRepositorySaveIsConflict() throws Exception {
      // Simulates the race between the pre-flight rejectDuplicateName read and the repository's
      // atomic SETNX. Must surface as 409, not 500.
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(redisRepo.findByPrincipal("USER", ADMIN_EMAIL)).thenReturn(List.of());
      doThrow(new DuplicateTokenNameException("race-loser-name already taken"))
          .when(redisRepo)
          .save(any(TokenRecord.class), anyString());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "race-loser-name"))))
          .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("user token with no expiresAt gets default max lifetime applied")
    void userTokenDefaultExpiryApplied() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      View roleView = new View();
      roleView.setName("api-minters");
      when(permissionService.getRoles(USER_EMAIL)).thenReturn(java.util.Set.of(roleView));
      when(redisRepo.findByPrincipal("USER", USER_EMAIL)).thenReturn(List.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", "t"))))
          .andExpect(status().isCreated());

      ArgumentCaptor<TokenRecord> captor = ArgumentCaptor.forClass(TokenRecord.class);
      verify(redisRepo).save(captor.capture(), anyString());
      String expiresAt = captor.getValue().getExpiresAt();
      assertThat(expiresAt).isNotNull();
      Instant expiry = Instant.parse(expiresAt);
      assertThat(expiry).isBefore(Instant.now().plus(91, ChronoUnit.DAYS));
      assertThat(expiry).isAfter(Instant.now().plus(89, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("SA token with no expiresAt is non-expiring (expiresAt null)")
    void saTokenNoExpiryIsNonExpiring() throws Exception {
      String saName = "ci-bot";
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(front50Service.getTokenEligibleServiceAccounts())
          .thenReturn(Calls.response(List.of(Map.of("name", saName))));
      when(redisRepo.findByPrincipal("SERVICE_ACCOUNT", saName)).thenReturn(List.of());

      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "name", "t",
                              "principalType", "SERVICE_ACCOUNT",
                              "principalId", saName))))
          .andExpect(status().isCreated());

      ArgumentCaptor<TokenRecord> captor = ArgumentCaptor.forClass(TokenRecord.class);
      verify(redisRepo).save(captor.capture(), anyString());
      assertThat(captor.getValue().getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("user token with expiresAt beyond max — 400")
    void userTokenExceedingMaxExpiryIsBadRequest() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      View roleView = new View();
      roleView.setName("api-minters");
      when(permissionService.getRoles(USER_EMAIL)).thenReturn(java.util.Set.of(roleView));
      when(redisRepo.findByPrincipal("USER", USER_EMAIL)).thenReturn(List.of());

      String tooFar = Instant.now().plus(200, ChronoUnit.DAYS).toString();
      mockMvc
          .perform(
              post("/auth/apiTokens")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(Map.of("name", "t", "expiresAt", tooFar))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "returned token contains single-use plaintext in 'token' field starting with prefix")
    void returnedTokenContainsPlaintext() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(redisRepo.findByPrincipal("USER", ADMIN_EMAIL)).thenReturn(List.of());

      String body =
          mockMvc
              .perform(
                  post("/auth/apiTokens")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(Map.of("name", "t"))))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      Map<?, ?> result = objectMapper.readValue(body, Map.class);
      String plaintext = (String) result.get("token");
      assertThat(plaintext).startsWith("spk_");
    }
  }

  // ---------------------------------------------------------------------------
  // DELETE /auth/apiTokens/{id}
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DELETE /auth/apiTokens/{id}")
  class RevokeToken {

    @Test
    @DisplayName("owner can revoke their own token — 204")
    void ownerCanRevoke() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      TokenRecord record = makeRecord(TOKEN_ID, USER_EMAIL, "USER", FUTURE_EXPIRY);
      when(redisRepo.findById(TOKEN_ID)).thenReturn(Optional.of(record));

      mockMvc.perform(delete("/auth/apiTokens/" + TOKEN_ID)).andExpect(status().isNoContent());

      verify(redisRepo).delete(TOKEN_ID, HASH_REF, record.getName(), "USER", USER_EMAIL);
    }

    @Test
    @DisplayName("non-owner cannot revoke another user's token — 403")
    void nonOwnerCannotRevoke() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);
      TokenRecord record = makeRecord("other-id", "bob@doordash.com", "USER", FUTURE_EXPIRY);
      when(redisRepo.findById("other-id")).thenReturn(Optional.of(record));

      mockMvc.perform(delete("/auth/apiTokens/other-id")).andExpect(status().isForbidden());

      verify(redisRepo, never()).delete(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("admin can revoke any token — 204")
    void adminCanRevokeAnyToken() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      TokenRecord record = makeRecord(TOKEN_ID, USER_EMAIL, "USER", FUTURE_EXPIRY);
      when(redisRepo.findById(TOKEN_ID)).thenReturn(Optional.of(record));

      mockMvc.perform(delete("/auth/apiTokens/" + TOKEN_ID)).andExpect(status().isNoContent());

      verify(redisRepo).delete(TOKEN_ID, HASH_REF, record.getName(), "USER", USER_EMAIL);
    }

    @Test
    @DisplayName("revoke of non-existent token — 404")
    void revokeNonExistentToken() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      // 404 fires from findById before isAdmin is consulted; stubbing isAdmin would violate
      // strict stubbing.
      when(redisRepo.findById("gone")).thenReturn(Optional.empty());

      mockMvc.perform(delete("/auth/apiTokens/gone")).andExpect(status().isNotFound());
    }
  }

  // ---------------------------------------------------------------------------
  // GET /auth/apiTokens/serviceAccounts
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /auth/apiTokens/serviceAccounts")
  class ListServiceAccounts {

    @Test
    @DisplayName("admin can list token-eligible service accounts")
    void adminListsSAs() throws Exception {
      setCurrentUser(ADMIN_EMAIL);
      when(permissionService.isAdmin(ADMIN_EMAIL)).thenReturn(true);
      when(front50Service.getTokenEligibleServiceAccounts())
          .thenReturn(Calls.response(List.of(Map.of("name", "ci-bot"))));

      mockMvc
          .perform(get("/auth/apiTokens/serviceAccounts"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].name").value("ci-bot"));
    }

    @Test
    @DisplayName("non-admin is denied — 403")
    void nonAdminDenied() throws Exception {
      setCurrentUser(USER_EMAIL);
      when(permissionService.isAdmin(USER_EMAIL)).thenReturn(false);

      mockMvc.perform(get("/auth/apiTokens/serviceAccounts")).andExpect(status().isForbidden());
    }
  }
}
