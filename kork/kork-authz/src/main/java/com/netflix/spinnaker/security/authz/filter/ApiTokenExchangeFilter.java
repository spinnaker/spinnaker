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

import com.netflix.spinnaker.kork.common.Header;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Verifier-side filter that lets an opaque Spinnaker API token ({@code spk_…}) work when presented
 * <em>directly</em> to a downstream service (e.g. an operator port-forwarding straight to
 * Front50/Clouddriver) rather than through Gate.
 *
 * <p>It runs just <em>before</em> {@link IdentityTokenAuthenticationFilter} and only acts when:
 *
 * <ul>
 *   <li>there is no {@code X-SPINNAKER-IDENTITY-TOKEN} on the request (the normal Gate-proxied hot
 *       path already carries one — never re-exchanged), and
 *   <li>a {@code spk_} token is present in {@code X-Spinnaker-Token} (preferred) or {@code
 *       Authorization: Bearer spk_…}.
 * </ul>
 *
 * <p>On a hit it exchanges the token (one cached round-trip via {@link ApiTokenExchangeClient}) for
 * the signed identity token Gate would have minted, injects it as the {@code
 * X-SPINNAKER-IDENTITY-TOKEN} header, and hands off — so {@link IdentityTokenAuthenticationFilter}
 * performs all verification and {@code SecurityContext} population through the single, normal trust
 * path. If the exchange fails the request proceeds unauthenticated (permissive fallback / anonymous
 * downstream), exactly as if no token had been supplied.
 */
public class ApiTokenExchangeFilter extends HttpFilter {

  private static final Logger log = LoggerFactory.getLogger(ApiTokenExchangeFilter.class);

  /** IAP-safe header carrying the API token; checked before {@code Authorization}. */
  static final String HEADER_X_SPINNAKER_TOKEN = "X-Spinnaker-Token";

  private static final String BEARER_PREFIX = "Bearer ";

  private final ApiTokenExchangeClient client;
  private final String tokenPrefix;

  public ApiTokenExchangeFilter(ApiTokenExchangeClient client, String tokenPrefix) {
    this.client = client;
    this.tokenPrefix = tokenPrefix;
  }

  /**
   * Build the filter when {@code authz.api-token-exchange.enabled} is set and a Gate URL is
   * available; otherwise return {@code null} so callers can leave the filter out of the chain.
   *
   * <p>{@code gateUrl} is the standard {@code services.gate.baseUrl}. When it is blank the filter
   * is disabled with a warning.
   */
  @Nullable
  public static ApiTokenExchangeFilter createIfEnabled(
      ApiTokenExchangeProperties properties, @Nullable String gateUrl) {
    if (!properties.isEnabled()) {
      return null;
    }
    if (gateUrl == null || gateUrl.isBlank()) {
      log.warn(
          "authz.api-token-exchange.enabled=true but services.gate.baseUrl is not set; direct spk_ "
              + "token support is disabled.");
      return null;
    }
    log.info(
        "Direct API-token (spk_) support enabled; tokens will be exchanged at {}{}",
        gateUrl,
        properties.getExchangePath());
    return new ApiTokenExchangeFilter(
        new ApiTokenExchangeClient(properties, gateUrl), properties.getTokenPrefix());
  }

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    // The Gate-proxied hot path already carries a minted identity token; never re-exchange.
    String existing = request.getHeader(Header.IDENTITY_TOKEN.getHeader());
    if (existing != null && !existing.isBlank()) {
      chain.doFilter(request, response);
      return;
    }

    String plaintext = extractToken(request);
    if (plaintext == null) {
      chain.doFilter(request, response);
      return;
    }

    // SECURITY: kork's AuthenticatedRequestFilter copies X-Spinnaker-* headers into MDC and
    // re-emits them on outbound calls, which would leak the plaintext token to other services'
    // logs. Clear it from MDC and wrap so it can't be re-read.
    HttpServletRequest scrubbed = request;
    if (request.getHeader(HEADER_X_SPINNAKER_TOKEN) != null) {
      MDC.remove(HEADER_X_SPINNAKER_TOKEN.toUpperCase(Locale.ROOT));
    }

    Optional<String> identityToken = client.exchange(plaintext);
    if (identityToken.isEmpty()) {
      // Unknown/expired/rejected token, or Gate unreachable: proceed unauthenticated.
      chain.doFilter(new TokenInjectingRequestWrapper(scrubbed, null), response);
      return;
    }

    log.debug("Exchanged a direct API token for an identity token");
    chain.doFilter(new TokenInjectingRequestWrapper(scrubbed, identityToken.get()), response);
  }

  /**
   * Reads the token from {@code X-Spinnaker-Token} (preferred, IAP-safe) or {@code Authorization:
   * Bearer spk_…}, requiring the configured prefix.
   */
  @Nullable
  private String extractToken(HttpServletRequest request) {
    String xToken = request.getHeader(HEADER_X_SPINNAKER_TOKEN);
    if (xToken != null && xToken.startsWith(tokenPrefix)) {
      return xToken;
    }
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX + tokenPrefix)) {
      return authHeader.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  /**
   * Hides {@code X-Spinnaker-Token} from all downstream code (so it can't be re-copied into MDC or
   * forwarded) and, when an identity token was minted, exposes it as the {@code
   * X-SPINNAKER-IDENTITY-TOKEN} header for {@link IdentityTokenAuthenticationFilter} to verify.
   */
  private static final class TokenInjectingRequestWrapper extends HttpServletRequestWrapper {
    private final String identityHeaderName = Header.IDENTITY_TOKEN.getHeader();
    @Nullable private final String identityToken;

    TokenInjectingRequestWrapper(HttpServletRequest request, @Nullable String identityToken) {
      super(request);
      this.identityToken = identityToken;
    }

    @Override
    public String getHeader(String name) {
      if (HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(name)) {
        return null;
      }
      if (identityToken != null && identityHeaderName.equalsIgnoreCase(name)) {
        return identityToken;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(name)) {
        return Collections.emptyEnumeration();
      }
      if (identityToken != null && identityHeaderName.equalsIgnoreCase(name)) {
        return Collections.enumeration(Collections.singletonList(identityToken));
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      var names =
          Collections.list(super.getHeaderNames()).stream()
              .filter(n -> !HEADER_X_SPINNAKER_TOKEN.equalsIgnoreCase(n))
              .filter(n -> !identityHeaderName.equalsIgnoreCase(n))
              .collect(Collectors.toList());
      if (identityToken != null) {
        names.add(identityHeaderName);
      }
      return Collections.enumeration(names);
    }
  }
}
