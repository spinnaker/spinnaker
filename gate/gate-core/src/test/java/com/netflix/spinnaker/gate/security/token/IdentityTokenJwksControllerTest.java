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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.token.IdentityTokenKeys;
import com.netflix.spinnaker.security.token.IdentityTokenSigningKeys;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenMinter;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenSettings;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Gate actually publishes its identity-token signing keys over {@code GET
 * /auth/jwks}.
 *
 * <p>Without this endpoint, downstream services (which derive Gate's JWKS URL from {@code
 * services.gate.baseUrl}) have no Gate keys, so every Gate-minted interactive-user token fails
 * verification. In permissive mode the request then falls back to unsigned identity headers (no
 * {@code SPINNAKER_ADMIN}/role authorities), so admin bypass and role ACLs silently stop applying
 * on every downstream sub-resource read/write (pipelines, clusters, application-permission saves).
 */
class IdentityTokenJwksControllerTest {

  private static SpinnakerTokenSettings settings() {
    SpinnakerTokenSettings settings = new SpinnakerTokenSettings();
    settings.setIssuer("gate");
    settings.setAudience(List.of("spinnaker"));
    settings.setValidity(Duration.ofMinutes(5));
    settings.setClockSkew(Duration.ofSeconds(30));
    return settings;
  }

  @Test
  @DisplayName("/auth/jwks serves a standard JWK set with public key material only")
  void publishesPublicKeysOnly() throws Exception {
    RSAKey signingKey = IdentityTokenKeys.generateRsaKey("gate-1");
    IdentityTokenJwksController controller =
        new IdentityTokenJwksController(IdentityTokenKeys.publicJwkSet(signingKey));

    Map<String, Object> body = controller.jwks();

    // A downstream RemoteJWKSet parses exactly this document.
    JWKSet published = JWKSet.parse(body);
    assertThat(published.getKeys()).hasSize(1);
    assertThat(published.getKeys().get(0).getKeyID()).isEqualTo("gate-1");
    assertThat(published.getKeys().get(0).isPrivate()).isFalse();
  }

  @Test
  @DisplayName("published JWKS lets a downstream verifier validate a Gate-minted admin token")
  void publishedJwksVerifiesAdminTokenDownstream() throws Exception {
    // Gate edge: sign with the private key, publish the public JWKS over /auth/jwks.
    RSAKey signingKey = IdentityTokenKeys.generateRsaKey("gate-1");
    SpinnakerTokenSettings settings = settings();
    String adminToken =
        new NimbusSpinnakerTokenMinter(signingKey, settings)
            .mint("admin@doordash.com", List.of("spinnaker-admins"), /* admin= */ true, false);

    IdentityTokenJwksController controller =
        new IdentityTokenJwksController(IdentityTokenKeys.publicJwkSet(signingKey));

    // Downstream service: fetch the JWKS (as a RemoteJWKSet would) and build its verifier from it.
    JWKSet downstreamKeys = JWKSet.parse(controller.jwks());
    SpinnakerTokenVerifier downstreamVerifier =
        new NimbusSpinnakerTokenVerifier(
            IdentityTokenKeys.immutableKeySource(downstreamKeys), settings);

    SpinnakerTokenClaims claims = downstreamVerifier.verify(adminToken);

    assertThat(claims.getSubject()).isEqualTo("admin@doordash.com");
    assertThat(claims.isAdmin()).isTrue();
    assertThat(claims.getRoles()).containsExactly("spinnaker-admins");
  }

  @Test
  @DisplayName(
      "config exposes the public JWKS bean (no private material) that the controller serves")
  void configExposesPublicJwksBean() {
    RSAKey signingKey = IdentityTokenKeys.generateRsaKey("gate-1");
    IdentityTokenSigningKeys signingKeys =
        new IdentityTokenSigningKeys(List.of(signingKey), signingKey);

    JWKSet publicJwks = new IdentityTokenConfiguration().identityTokenPublicJwks(signingKeys);

    assertThat(publicJwks.getKeys()).hasSize(1);
    assertThat(publicJwks.getKeys().get(0).isPrivate()).isFalse();
  }

  @Test
  @DisplayName(
      "during rotation the JWKS publishes both keys so tokens signed by either still verify")
  void publishesAllKeysDuringRotation() throws Exception {
    RSAKey oldKey = IdentityTokenKeys.generateRsaKey("gate-2026-06");
    RSAKey newKey = IdentityTokenKeys.generateRsaKey("gate-2026-07");
    SpinnakerTokenSettings settings = settings();

    // Overlap window: new key is active, both are published.
    IdentityTokenSigningKeys signingKeys =
        new IdentityTokenSigningKeys(List.of(newKey, oldKey), newKey);
    JWKSet publicJwks = new IdentityTokenConfiguration().identityTokenPublicJwks(signingKeys);
    assertThat(publicJwks.getKeys()).hasSize(2);

    // A token still in flight signed by the outgoing key verifies against the published set.
    String inFlight =
        new NimbusSpinnakerTokenMinter(oldKey, settings)
            .mint("user@doordash.com", List.of("dev"), false, false);
    SpinnakerTokenVerifier verifier =
        new NimbusSpinnakerTokenVerifier(
            IdentityTokenKeys.immutableKeySource(publicJwks), settings);
    assertThat(verifier.verify(inFlight).getSubject()).isEqualTo("user@doordash.com");

    // New tokens (signed by the active key) also verify.
    String fresh =
        new NimbusSpinnakerTokenMinter(newKey, settings)
            .mint("user@doordash.com", List.of("dev"), false, false);
    assertThat(verifier.verify(fresh).getSubject()).isEqualTo("user@doordash.com");
  }
}
