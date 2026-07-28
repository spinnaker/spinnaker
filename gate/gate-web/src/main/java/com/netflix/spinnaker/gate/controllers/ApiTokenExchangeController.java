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

import com.netflix.spinnaker.gate.security.apitoken.ApiTokenHashing;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenProperties;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenService;
import com.netflix.spinnaker.gate.security.apitoken.TokenRecord;
import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.v3.oas.annotations.Operation;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-side token exchange: resolves an opaque Spinnaker API token ({@code spk_…}) and returns
 * the signed identity-token JWT Gate would have minted and propagated had the request come through
 * Gate. This lets downstream services accept a {@code spk_} token presented <em>directly</em> (e.g.
 * via port-forward) by exchanging it here first — see {@code
 * com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeFilter}.
 *
 * <p>The token itself is the credential, so the endpoint is unauthenticated (permitted in {@code
 * AuthConfig}); it returns the same authority that using the token through Gate would, and {@code
 * 401} for an unknown/expired token. Enabled only when {@code api-tokens.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/auth/apiTokens/exchange")
@ConditionalOnProperty("api-tokens.enabled")
@RequiredArgsConstructor
public class ApiTokenExchangeController {

  private final ApiTokenService apiTokenService;
  private final GateIdentityService identityService;
  private final ApiTokenProperties properties;

  @Operation(summary = "Exchange an opaque API token for a signed identity token")
  @PostMapping
  public Map<String, String> exchange(@RequestBody ExchangeRequest request) {
    String plaintext = request == null ? null : request.token();
    if (plaintext == null || !plaintext.startsWith(properties.getTokenPrefix())) {
      throw unauthorized();
    }

    String tokenHash = ApiTokenHashing.sha256Hex(plaintext);
    Optional<TokenRecord> resolved = apiTokenService.resolveByHash(tokenHash);
    if (resolved.isEmpty()) {
      throw unauthorized();
    }

    TokenRecord record = resolved.get();
    String principalId = record.getPrincipalId();

    // Resolve roles and mint exactly as ApiTokenAuthenticationFilter does on the through-Gate
    // path, so the downstream identity is identical. allowAnonymous keeps any stale
    // X-SPINNAKER-USER
    // in MDC from leaking into role resolution.
    Set<String> roles =
        AuthenticatedRequest.allowAnonymous(
            () -> new LinkedHashSet<>(identityService.rolesFor(principalId)));
    String identityToken = identityService.mintToken(principalId, roles);
    if (identityToken == null) {
      // No signing key configured (authorization disabled); there is no signed token to hand back.
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Identity token minting is not configured");
    }

    apiTokenService.touchLastUsedAsync(record.getId(), tokenHash);
    log.debug("Exchanged API token id={} for an identity token", record.getId());
    return Map.of("identityToken", identityToken);
  }

  private static ResponseStatusException unauthorized() {
    // Uniform response so the endpoint is not a token-existence oracle.
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API token");
  }

  /** Request body: {@code {"token": "spk_…"}}. */
  public record ExchangeRequest(String token) {}
}
