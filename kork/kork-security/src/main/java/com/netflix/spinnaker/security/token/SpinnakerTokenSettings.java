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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for minting and verifying Spinnaker identity tokens (claims and validation
 * tolerances), bound from the {@code authz.token} property prefix.
 *
 * <p>The authorization master switch is <em>not</em> here — it lives at the top-level {@code
 * authz.enabled} ({@link AuthorizationProperties}). This class only carries token-specific claim
 * and validation settings.
 */
@ConfigurationProperties("authz.token")
public class SpinnakerTokenSettings {

  /** Expected/emitted {@code iss} claim. */
  private String issuer = "spinnaker";

  /** Expected/emitted {@code aud} claim(s). */
  private List<String> audience = new ArrayList<>(List.of("spinnaker"));

  /** Lifetime applied to minted tokens via the {@code exp} claim. */
  private Duration validity = Duration.ofMinutes(5);

  /** Clock-skew tolerance applied when validating {@code exp}/{@code nbf}/{@code iat}. */
  private Duration clockSkew = Duration.ofSeconds(60);

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public List<String> getAudience() {
    return audience;
  }

  public void setAudience(List<String> audience) {
    this.audience = audience;
  }

  public Duration getValidity() {
    return validity;
  }

  public void setValidity(Duration validity) {
    this.validity = validity;
  }

  public Duration getClockSkew() {
    return clockSkew;
  }

  public void setClockSkew(Duration clockSkew) {
    this.clockSkew = clockSkew;
  }
}
