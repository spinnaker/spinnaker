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

import com.netflix.spinnaker.gate.security.SpinnakerUser;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenHashing;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenProperties;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenService;
import com.netflix.spinnaker.gate.security.apitoken.RedisApiTokenRepository;
import com.netflix.spinnaker.gate.security.apitoken.RedisApiTokenRepository.DuplicateTokenNameException;
import com.netflix.spinnaker.gate.security.apitoken.TokenRecord;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * API token CRUD controller backed by Redis (via {@link RedisApiTokenRepository}). Authorization is
 * enforced here; the repository performs no authz of its own.
 *
 * <p>Enabled only when {@code api-tokens.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/auth/apiTokens")
@ConditionalOnProperty("api-tokens.enabled")
@RequiredArgsConstructor
public class ApiTokenController {

  /** Hard cap on token name length. Mirrors the UI cap in CreateApiTokenModal.tsx. */
  static final int MAX_TOKEN_NAME_LENGTH = 64;

  private final RedisApiTokenRepository redisRepo;
  private final PermissionService permissionService;
  private final ApiTokenProperties properties;
  private final Front50Service front50Service;
  private final ApiTokenService apiTokenService;

  // ---------------------------------------------------------------------------
  // GET /auth/apiTokens
  // ---------------------------------------------------------------------------

  @Operation(summary = "List API tokens visible to the caller")
  @GetMapping
  public List<Map<String, Object>> list(@Parameter(hidden = true) @SpinnakerUser User user) {
    requireAuthenticated(user);
    boolean isAdmin = permissionService.isAdmin(user.getUsername());

    List<TokenRecord> userTokens = redisRepo.findByPrincipal("USER", user.getUsername());

    // Admins also see all service-account tokens so they can manage and revoke them.
    if (isAdmin) {
      List<TokenRecord> saTokens = redisRepo.findAllByPrincipalType("SERVICE_ACCOUNT");
      List<TokenRecord> merged = new ArrayList<>(userTokens);
      merged.addAll(saTokens);
      return merged.stream().map(this::toPublicMap).collect(Collectors.toList());
    }

    return userTokens.stream().map(this::toPublicMap).collect(Collectors.toList());
  }

  // ---------------------------------------------------------------------------
  // GET /auth/apiTokens/admin/users
  // ---------------------------------------------------------------------------

  @Operation(summary = "List all user tokens across all principals (admin only)")
  @GetMapping("/admin/users")
  public List<Map<String, Object>> listAllUserTokens(
      @Parameter(hidden = true) @SpinnakerUser User user) {
    requireAuthenticated(user);
    if (!permissionService.isAdmin(user.getUsername())) {
      throw new AccessDeniedException("Only admins may list all user tokens");
    }
    return redisRepo.findAllByPrincipalType("USER").stream()
        .map(this::toPublicMap)
        .collect(Collectors.toList());
  }

  // ---------------------------------------------------------------------------
  // POST /auth/apiTokens
  // ---------------------------------------------------------------------------

  @Operation(summary = "Create an API token")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @Parameter(hidden = true) @SpinnakerUser User user,
      @RequestBody CreateApiTokenRequest request) {
    requireAuthenticated(user);

    boolean isAdmin = permissionService.isAdmin(user.getUsername());
    String principalType = request.principalType() == null ? "USER" : request.principalType();

    String effectivePrincipalId;
    if ("SERVICE_ACCOUNT".equalsIgnoreCase(principalType)) {
      if (!isAdmin) {
        throw new AccessDeniedException("Only admins may create service account tokens");
      }
      effectivePrincipalId = request.principalId();
      validateServiceAccountPrincipal(effectivePrincipalId);
    } else {
      requireMintingRole(user);
      effectivePrincipalId = user.getUsername();
      principalType = "USER";
    }

    String tokenName = validateTokenName(request.name());

    rejectDuplicateName(tokenName, effectivePrincipalId, principalType);

    String resolvedExpiry = resolveExpiry(request.expiresAt(), principalType);

    String plaintext = generateToken();
    String tokenHash = ApiTokenHashing.sha256Hex(plaintext);

    TokenRecord record = new TokenRecord();
    record.setId(UUID.randomUUID().toString());
    record.setName(tokenName);
    record.setPrincipalId(effectivePrincipalId);
    record.setPrincipalType(principalType.toUpperCase());
    record.setCreatedByUserId(user.getUsername());
    record.setCreatedAt(Instant.now().toString());
    record.setExpiresAt(resolvedExpiry);

    try {
      redisRepo.save(record, tokenHash);
    } catch (DuplicateTokenNameException e) {
      // Lost the SETNX race against a concurrent create — pre-flight rejectDuplicateName above is
      // only a fast-path UX optimisation; the repository SETNX is the source of truth.
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
    log.info(
        "Created API token id={} name='{}' principal={}/{}",
        record.getId(),
        tokenName,
        principalType,
        effectivePrincipalId);

    Map<String, Object> response = toPublicMap(record);
    // Return plaintext once — it will never be recoverable again
    response.put("token", plaintext);
    return response;
  }

  // ---------------------------------------------------------------------------
  // DELETE /auth/apiTokens/{id}
  // ---------------------------------------------------------------------------

  @Operation(summary = "Revoke an API token")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@Parameter(hidden = true) @SpinnakerUser User user, @PathVariable String id) {
    requireAuthenticated(user);

    Optional<TokenRecord> opt = redisRepo.findById(id);
    if (opt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found: " + id);
    }

    TokenRecord record = opt.get();
    boolean isAdmin = permissionService.isAdmin(user.getUsername());

    if (!isAdmin
        && !("USER".equalsIgnoreCase(record.getPrincipalType())
            && user.getUsername().equals(record.getPrincipalId()))) {
      throw new AccessDeniedException("You do not own this token");
    }

    String hashRef = record.getHashRef();
    if (hashRef == null || hashRef.isBlank()) {
      // Defensive: save() requires a non-blank tokenHash, so this only happens for legacy or
      // out-of-band Redis writes. Refuse rather than leak an un-revoked hash key.
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Token id=" + record.getId() + " is missing hashRef; cannot guarantee revocation");
    }
    redisRepo.delete(
        record.getId(),
        hashRef,
        record.getName(),
        record.getPrincipalType(),
        record.getPrincipalId());
  }

  // ---------------------------------------------------------------------------
  // GET /auth/apiTokens/serviceAccounts
  // ---------------------------------------------------------------------------

  @Operation(summary = "List token-eligible service accounts (admin only)")
  @GetMapping("/serviceAccounts")
  public List<Map> getApiServiceAccounts(@Parameter(hidden = true) @SpinnakerUser User user) {
    requireAuthenticated(user);
    if (!permissionService.isAdmin(user.getUsername())) {
      throw new AccessDeniedException("Only admins may list token-eligible service accounts");
    }
    List<Map> result = Retrofit2SyncCall.execute(front50Service.getTokenEligibleServiceAccounts());
    return result != null ? result : List.of();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void requireAuthenticated(User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
  }

  private void requireMintingRole(User user) {
    if (apiTokenService.canMintApiTokens(user.getUsername())) {
      return;
    }
    // Distinguish "no roles configured" from "user not in any configured role" for a useful error.
    if (properties.getAllowedMintingRoles().isEmpty()) {
      throw new AccessDeniedException("API token minting is not configured for any role");
    }
    throw new AccessDeniedException(
        "User is not in any allowed minting role: " + properties.getAllowedMintingRoles());
  }

  /**
   * Validates and normalizes the requested token name. The returned value is the trimmed name that
   * should be persisted; callers must not use {@code request.name()} directly after this point.
   */
  private String validateTokenName(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token name is required");
    }
    String name = rawName.trim();
    if (name.length() > MAX_TOKEN_NAME_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Token name must be at most " + MAX_TOKEN_NAME_LENGTH + " characters");
    }
    // Reject ISO control characters (NUL, \t, \n, \r, ANSI escapes, etc.) — log-injection and
    // display-spoofing risk once the name is echoed in API responses and UI lists.
    for (int i = 0; i < name.length(); i++) {
      if (Character.isISOControl(name.charAt(i))) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Token name must not contain control characters");
      }
    }
    return name;
  }

  private void rejectDuplicateName(String name, String principalId, String principalType) {
    List<TokenRecord> existing = redisRepo.findByPrincipal(principalType, principalId);
    boolean taken = existing.stream().anyMatch(t -> name.equals(t.getName()));
    if (taken) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "A token named '"
              + name
              + "' already exists for this principal. Choose a different name.");
    }
  }

  private void validateServiceAccountPrincipal(String principalId) {
    if (principalId == null || principalId.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "principalId is required for SERVICE_ACCOUNT tokens");
    }
    List<Map> eligibleSAs =
        Retrofit2SyncCall.execute(front50Service.getTokenEligibleServiceAccounts());
    boolean valid =
        eligibleSAs != null
            && eligibleSAs.stream().anyMatch(sa -> principalId.equals(sa.get("name")));
    if (!valid) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Service account '"
              + principalId
              + "' is not declared as a token-eligible service account");
    }
  }

  private String resolveExpiry(String requestedExpiry, String principalType) {
    boolean isSA = "SERVICE_ACCOUNT".equalsIgnoreCase(principalType);

    if (requestedExpiry == null || requestedExpiry.isBlank()) {
      if (isSA) return null;
      return Instant.now()
          .plus(properties.getMaxUserTokenLifetimeDays(), ChronoUnit.DAYS)
          .toString();
    }

    int maxDays =
        isSA
            ? properties.getMaxServiceAccountTokenLifetimeDays()
            : properties.getMaxUserTokenLifetimeDays();
    Instant ceiling = Instant.now().plus(maxDays, ChronoUnit.DAYS);
    Instant requested;
    try {
      requested = Instant.parse(requestedExpiry);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid expiresAt: " + requestedExpiry);
    }
    if (requested.isAfter(ceiling)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "expiresAt exceeds max token lifetime of "
              + maxDays
              + " days for "
              + principalType
              + " tokens");
    }
    return requested.toString();
  }

  private String generateToken() {
    byte[] bytes = new byte[properties.getTokenRandomBytes()];
    new SecureRandom().nextBytes(bytes);
    return properties.getTokenPrefix() + HexFormat.of().formatHex(bytes);
  }

  private Map<String, Object> toPublicMap(TokenRecord record) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", record.getId());
    m.put("name", record.getName());
    m.put("principalId", record.getPrincipalId());
    m.put("principalType", record.getPrincipalType());
    m.put("createdByUserId", record.getCreatedByUserId());
    m.put("createdAt", record.getCreatedAt());
    if (record.getExpiresAt() != null) m.put("expiresAt", record.getExpiresAt());
    if (record.getLastUsedAt() != null) m.put("lastUsedAt", record.getLastUsedAt());
    return m;
  }
}
