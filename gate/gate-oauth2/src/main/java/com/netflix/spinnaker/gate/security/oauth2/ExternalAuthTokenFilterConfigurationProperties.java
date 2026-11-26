/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.security.oauth2;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the {@link ExternalAuthTokenFilter}.
 *
 * <p>These properties control the HTTP client timeouts when calling the OAuth2 user-info endpoint,
 * and optional local validation of JWT bearer tokens.
 */
@ConfigurationProperties(prefix = "security.oauth2.external-auth-token-filter")
@Data
public class ExternalAuthTokenFilterConfigurationProperties {

  /** Connection timeout in milliseconds for the user-info endpoint HTTP client. */
  private long connectTimeoutMs = 5000;

  /** Read timeout in milliseconds for the user-info endpoint HTTP client. */
  private long readTimeoutMs = 5000;

  /** Local validation of JWT bearer tokens. Off unless {@code jwt.issuer-uri} is set. */
  private Jwt jwt = new Jwt();

  /**
   * Validates bearer tokens as JWTs issued for this Gate, instead of only sending them to the
   * user-info endpoint. Use it for authorization servers whose access tokens for Gate's own API
   * can't be checked at their user-info endpoint, for example tokens bound to Gate with the RFC
   * 8707 {@code resource} parameter.
   */
  @Data
  public static class Jwt {

    /**
     * Issuer the tokens must come from, matched exactly against the {@code iss} claim. Setting it
     * turns JWT validation on.
     */
    private String issuerUri;

    /**
     * JWK Set URI with the issuer's signing keys. Optional: when unset, it's discovered from the
     * issuer's OpenID Provider or authorization server metadata on the first token.
     */
    private String jwkSetUri;

    /**
     * Accepted audiences. Required with {@code issuer-uri}: a token's {@code aud} must contain one.
     */
    private List<String> audiences = new ArrayList<>();

    /**
     * Scopes a token must have, read from its {@code scp} or {@code scope} claim (a space-separated
     * string or a list). Empty means no scope check.
     */
    private List<String> requiredScopes = new ArrayList<>();

    /**
     * Whether a bearer token that fails JWT validation is still sent to the user-info endpoint.
     * Keep this on while clients still send tokens that only the user-info endpoint accepts (for
     * example opaque tokens). Turn it off to accept only JWTs issued for this Gate.
     */
    private boolean userInfoFallback = true;

    public boolean isEnabled() {
      return issuerUri != null && !issuerUri.isBlank();
    }
  }
}
