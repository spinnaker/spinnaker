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

package com.netflix.spinnaker.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared inbound identity-token verification tuning, bound from the {@code authz.verifier} prefix
 * and consumed by <em>every</em> verifier service (Clouddriver, Orca, Echo, Igor, Front50, Keel,
 * …).
 *
 * <p>The trusted JWKS endpoints are <em>not</em> configured here. Each verifier derives them from
 * the service-to-service URLs it is already configured with — {@code services.gate.baseUrl}
 * (interactive-user tokens) and {@code services.front50.baseUrl} (run-as tokens) — by appending
 * {@link IdentityTokenKeys#JWKS_PATH} ({@code /auth/jwks}). This class only carries the HTTP fetch
 * tuning for retrieving those JWKS documents.
 *
 * <p>Keys from all derived endpoints are consulted (by {@code kid}) when verifying a token,
 * tolerating overlapping rotation windows. When no endpoint can be resolved no token can be
 * verified; with authorization disabled ({@code authz.enabled=false}) requests then fall back to
 * the legacy unsigned identity headers.
 */
@ConfigurationProperties("authz.verifier")
public class IdentityTokenVerifierProperties {

  /** HTTP connect timeout (ms) for fetching remote JWKS documents. */
  private int connectTimeoutMillis = 2000;

  /** HTTP read timeout (ms) for fetching remote JWKS documents. */
  private int readTimeoutMillis = 2000;

  /** Maximum size (bytes) of a fetched JWKS document. */
  private int sizeLimitBytes = 51200;

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

  public int getSizeLimitBytes() {
    return sizeLimitBytes;
  }

  public void setSizeLimitBytes(int sizeLimitBytes) {
    this.sizeLimitBytes = sizeLimitBytes;
  }
}
