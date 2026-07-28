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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Verifier-side configuration (bound from {@code authz.api-token-exchange}) for accepting an opaque
 * Spinnaker API token ({@code spk_…}) presented <em>directly</em> to a downstream service — e.g.
 * when an operator port-forwards straight to Front50/Clouddriver instead of going through Gate.
 *
 * <p>When enabled, {@link ApiTokenExchangeFilter} detects such a token and exchanges it (a single
 * cached round-trip) at Gate's {@code /auth/apiTokens/exchange} endpoint for the same signed
 * identity token Gate would have minted and propagated had the request gone through Gate. The
 * service then verifies that JWT through its normal {@link IdentityTokenAuthenticationConverter}
 * path, so the resulting {@code SecurityContext} is identical to the Gate-proxied case.
 *
 * <p>Disabled by default; the normal hot path (a request already carrying a Gate-minted {@code
 * X-SPINNAKER-IDENTITY-TOKEN}) never triggers an exchange even when enabled.
 */
@ConfigurationProperties("authz.api-token-exchange")
public class ApiTokenExchangeProperties {

  /** Master switch. When {@code false} (default) the exchange filter is not installed. */
  private boolean enabled = false;

  /**
   * Path of Gate's exchange endpoint, appended to the Gate base URL (the standard {@code
   * services.gate.baseUrl}).
   */
  private String exchangePath = "/auth/apiTokens/exchange";

  /**
   * Token prefix used to recognize a Spinnaker API token; must match Gate's {@code
   * api-tokens.token-prefix}.
   */
  private String tokenPrefix = "spk_";

  /**
   * Upper bound on how long an exchanged identity token is cached per API token. The effective TTL
   * is the lesser of this and the JWT's own {@code exp} (minus {@link #clockSkewSeconds}); keeping
   * it at or below {@code authz.token.expiry} avoids serving an expired token downstream.
   */
  private Duration cacheTtl = Duration.ofMinutes(5);

  /** Maximum number of distinct API tokens whose exchanged JWT is cached. */
  private long cacheMaximumSize = 10_000;

  /**
   * Short window for which an unsuccessful exchange (unknown/expired/rejected token) is remembered,
   * so a stream of requests bearing the same bad token does not hammer Gate. Bounded by {@link
   * #cacheTtl}.
   */
  private Duration negativeCacheTtl = Duration.ofSeconds(30);

  /** Clock-skew margin subtracted from the JWT {@code exp} when deciding cache expiry. */
  private int clockSkewSeconds = 30;

  /** HTTP connect timeout (ms) for the exchange call. */
  private int connectTimeoutMillis = 2000;

  /** HTTP read timeout (ms) for the exchange call. */
  private int readTimeoutMillis = 2000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getExchangePath() {
    return exchangePath;
  }

  public void setExchangePath(String exchangePath) {
    this.exchangePath = exchangePath;
  }

  public String getTokenPrefix() {
    return tokenPrefix;
  }

  public void setTokenPrefix(String tokenPrefix) {
    this.tokenPrefix = tokenPrefix;
  }

  public Duration getCacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }

  public long getCacheMaximumSize() {
    return cacheMaximumSize;
  }

  public void setCacheMaximumSize(long cacheMaximumSize) {
    this.cacheMaximumSize = cacheMaximumSize;
  }

  public Duration getNegativeCacheTtl() {
    return negativeCacheTtl;
  }

  public void setNegativeCacheTtl(Duration negativeCacheTtl) {
    this.negativeCacheTtl = negativeCacheTtl;
  }

  public int getClockSkewSeconds() {
    return clockSkewSeconds;
  }

  public void setClockSkewSeconds(int clockSkewSeconds) {
    this.clockSkewSeconds = clockSkewSeconds;
  }

  public int getConnectTimeoutMillis() {
    return connectTimeoutMillis;
  }

  public void setConnectTimeoutMillis(int connectTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
  }

  public int getReadTimeoutMillis() {
    return readTimeoutMillis;
  }

  public void setReadTimeoutMillis(int readTimeoutMillis) {
    this.readTimeoutMillis = readTimeoutMillis;
  }
}
