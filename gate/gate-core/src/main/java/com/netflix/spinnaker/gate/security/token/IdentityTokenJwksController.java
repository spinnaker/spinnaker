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

package com.netflix.spinnaker.gate.security.token;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes Gate's identity-token signing keys as a public JWK set so downstream services (front50,
 * clouddriver, orca, echo, igor) can verify the interactive-user identity tokens Gate mints at the
 * edge.
 *
 * <p>Gate is the only minter of interactive-user identity tokens; without this endpoint a verifier
 * (which derives Gate's JWKS URL from {@code services.gate.baseUrl}) has no Gate keys, so every
 * Gate-minted token fails verification. When authorization is disabled ({@code
 * authz.enabled=false}) the request then falls back to the legacy unsigned identity headers, which
 * do <em>not</em> carry the {@code SPINNAKER_ADMIN}/role authorities — so admin bypass and
 * role-based ACLs only take effect at the Gate edge, and every owner-enforced sub-resource
 * read/write downstream is denied.
 *
 * <p>This mirrors Front50's {@code RunAsTokenController#jwks()} for run-as tokens. The endpoint
 * must be reachable without authentication (it serves public key material only); see {@code
 * AuthConfig} where {@code /auth/jwks} is permitted.
 */
@RestController
@RequestMapping("/auth")
public class IdentityTokenJwksController {

  private final JWKSet identityTokenPublicJwks;

  public IdentityTokenJwksController(JWKSet identityTokenPublicJwks) {
    this.identityTokenPublicJwks = identityTokenPublicJwks;
  }

  @GetMapping("/jwks")
  public Map<String, Object> jwks() {
    // Public parts only; the signing key's private material is never serialized here.
    return identityTokenPublicJwks.toJSONObject();
  }
}
