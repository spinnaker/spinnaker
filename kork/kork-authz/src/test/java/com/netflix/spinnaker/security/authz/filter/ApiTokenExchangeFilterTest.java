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

package com.netflix.spinnaker.security.authz.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.common.Header;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiTokenExchangeFilterTest {

  private static final String IDENTITY_HEADER = Header.IDENTITY_TOKEN.getHeader();
  private static final String JWT = "minted.identity.jwt";

  /** A client subclass that returns a canned result without any HTTP, and counts calls. */
  private static class StubClient extends ApiTokenExchangeClient {
    final AtomicInteger calls = new AtomicInteger();
    private final Optional<String> result;

    StubClient(Optional<String> result) {
      super(baseProps(), "http://localhost");
      this.result = result;
    }

    @Override
    public Optional<String> exchange(String plaintextToken) {
      calls.incrementAndGet();
      return result;
    }
  }

  private static ApiTokenExchangeProperties baseProps() {
    ApiTokenExchangeProperties p = new ApiTokenExchangeProperties();
    p.setEnabled(true);
    return p;
  }

  private ApiTokenExchangeFilter filterWith(StubClient client) {
    return new ApiTokenExchangeFilter(client, "spk_");
  }

  @Test
  @DisplayName("injects the exchanged identity token for an X-Spinnaker-Token spk_ header")
  void injectsForXSpinnakerToken() throws Exception {
    StubClient client = new StubClient(Optional.of(JWT));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiTokenExchangeFilter.HEADER_X_SPINNAKER_TOKEN, "spk_abc");
    MockFilterChain chain = new MockFilterChain();

    filterWith(client).doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed.getHeader(IDENTITY_HEADER)).isEqualTo(JWT);
    // The opaque token is stripped from the request so it can't leak downstream.
    assertThat(passed.getHeader(ApiTokenExchangeFilter.HEADER_X_SPINNAKER_TOKEN)).isNull();
    assertThat(client.calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("injects the exchanged identity token for an Authorization: Bearer spk_ header")
  void injectsForBearerToken() throws Exception {
    StubClient client = new StubClient(Optional.of(JWT));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer spk_abc");
    MockFilterChain chain = new MockFilterChain();

    filterWith(client).doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed.getHeader(IDENTITY_HEADER)).isEqualTo(JWT);
    assertThat(client.calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("never re-exchanges when an identity token is already present (Gate hot path)")
  void skipsWhenIdentityTokenPresent() throws Exception {
    StubClient client = new StubClient(Optional.of(JWT));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(IDENTITY_HEADER, "existing.gate.jwt");
    request.addHeader(ApiTokenExchangeFilter.HEADER_X_SPINNAKER_TOKEN, "spk_abc");
    MockFilterChain chain = new MockFilterChain();

    filterWith(client).doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed.getHeader(IDENTITY_HEADER)).isEqualTo("existing.gate.jwt");
    assertThat(client.calls.get()).isZero();
  }

  @Test
  @DisplayName("passes through untouched when no API token is present")
  void passesThroughWithoutToken() throws Exception {
    StubClient client = new StubClient(Optional.of(JWT));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockFilterChain chain = new MockFilterChain();

    filterWith(client).doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed.getHeader(IDENTITY_HEADER)).isNull();
    assertThat(client.calls.get()).isZero();
  }

  @Test
  @DisplayName("proceeds unauthenticated (no identity header) when the exchange fails")
  void proceedsUnauthenticatedOnFailedExchange() throws Exception {
    StubClient client = new StubClient(Optional.empty());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiTokenExchangeFilter.HEADER_X_SPINNAKER_TOKEN, "spk_bad");
    MockFilterChain chain = new MockFilterChain();

    filterWith(client).doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed.getHeader(IDENTITY_HEADER)).isNull();
    assertThat(passed.getHeader(ApiTokenExchangeFilter.HEADER_X_SPINNAKER_TOKEN)).isNull();
    assertThat(client.calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("ignores a non-spk_ Authorization bearer token")
  void ignoresNonSpkBearer() throws Exception {
    StubClient client = new StubClient(Optional.of(JWT));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer eyJhbGciOi.something.else");
    MockFilterChain chain = new MockFilterChain();

    filterWith(client).doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed.getHeader(IDENTITY_HEADER)).isNull();
    assertThat(client.calls.get()).isZero();
  }

  @Test
  @DisplayName("createIfEnabled returns null when disabled or no Gate URL")
  void createIfEnabledGuards() {
    ApiTokenExchangeProperties disabled = new ApiTokenExchangeProperties();
    assertThat(ApiTokenExchangeFilter.createIfEnabled(disabled, "http://spin-gate:8084")).isNull();

    ApiTokenExchangeProperties enabled = new ApiTokenExchangeProperties();
    enabled.setEnabled(true);
    // Enabled but no services.gate.baseUrl -> disabled.
    assertThat(ApiTokenExchangeFilter.createIfEnabled(enabled, "")).isNull();
    assertThat(ApiTokenExchangeFilter.createIfEnabled(enabled, null)).isNull();
    // Enabled with services.gate.baseUrl -> installed.
    assertThat(ApiTokenExchangeFilter.createIfEnabled(enabled, "http://spin-gate:8084"))
        .isNotNull();
  }
}
