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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.controllers.ApiTokenExchangeController.ExchangeRequest;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenProperties;
import com.netflix.spinnaker.gate.security.apitoken.ApiTokenService;
import com.netflix.spinnaker.gate.security.apitoken.TokenRecord;
import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ApiTokenExchangeControllerTest {

  @Mock ApiTokenService apiTokenService;
  @Mock GateIdentityService identityService;

  ApiTokenProperties properties;
  ApiTokenExchangeController controller;

  private static final String PRINCIPAL = "bob@doordash.com";

  @BeforeEach
  void setup() {
    properties = new ApiTokenProperties();
    controller = new ApiTokenExchangeController(apiTokenService, identityService, properties);
  }

  private TokenRecord record() {
    TokenRecord record = new TokenRecord();
    record.setId("token-id-1");
    record.setPrincipalId(PRINCIPAL);
    record.setPrincipalType("USER");
    return record;
  }

  @Test
  @DisplayName("valid token returns the minted identity token")
  void validTokenMints() {
    when(apiTokenService.resolveByHash(anyString())).thenReturn(Optional.of(record()));
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of("deploy-team"));
    when(identityService.mintToken(eqPrincipal(), any())).thenReturn("signed.jwt");

    Map<String, String> result = controller.exchange(new ExchangeRequest("spk_abc"));

    assertThat(result).containsEntry("identityToken", "signed.jwt");
    verify(apiTokenService).touchLastUsedAsync("token-id-1", sha256("spk_abc"));
  }

  @Test
  @DisplayName("token without the expected prefix is rejected with 401")
  void wrongPrefixRejected() {
    assertThatThrownBy(() -> controller.exchange(new ExchangeRequest("bearer-xyz")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("unknown token is rejected with 401")
  void unknownTokenRejected() {
    when(apiTokenService.resolveByHash(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.exchange(new ExchangeRequest("spk_missing")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("no signing key (mint returns null) yields 503")
  void noMinterYields503() {
    when(apiTokenService.resolveByHash(anyString())).thenReturn(Optional.of(record()));
    when(identityService.rolesFor(PRINCIPAL)).thenReturn(Set.of());
    when(identityService.mintToken(eqPrincipal(), any())).thenReturn(null);

    assertThatThrownBy(() -> controller.exchange(new ExchangeRequest("spk_abc")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  private static String eqPrincipal() {
    return org.mockito.ArgumentMatchers.eq(PRINCIPAL);
  }

  private static String sha256(String value) {
    return com.netflix.spinnaker.gate.security.apitoken.ApiTokenHashing.sha256Hex(value);
  }
}
