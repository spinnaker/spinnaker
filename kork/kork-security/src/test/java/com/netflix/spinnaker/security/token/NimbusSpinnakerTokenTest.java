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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class NimbusSpinnakerTokenTest {

  private static SpinnakerTokenSettings settings() {
    SpinnakerTokenSettings settings = new SpinnakerTokenSettings();
    settings.setIssuer("gate");
    settings.setAudience(List.of("spinnaker"));
    settings.setValidity(Duration.ofMinutes(5));
    settings.setClockSkew(Duration.ofSeconds(30));
    return settings;
  }

  private static SpinnakerTokenVerifier verifierFor(
      SpinnakerTokenSettings settings, RSAKey... keys) {
    return new NimbusSpinnakerTokenVerifier(
        IdentityTokenKeys.immutableKeySource(IdentityTokenKeys.publicJwkSet(keys)), settings);
  }

  @Test
  void mintAndVerifyRoundTrip() {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");
    SpinnakerTokenSettings settings = settings();
    SpinnakerTokenMinter minter = new NimbusSpinnakerTokenMinter(key, settings);

    String token = minter.mint("alice@example.com", List.of("dev", "ops"), true, false);

    SpinnakerTokenClaims claims = verifierFor(settings, key).verify(token);
    assertThat(claims.getSubject()).isEqualTo("alice@example.com");
    assertThat(claims.getRoles()).containsExactly("dev", "ops");
    assertThat(claims.isAdmin()).isTrue();
    assertThat(claims.isAccountManager()).isFalse();
    assertThat(claims.getIssuer()).isEqualTo("gate");
    assertThat(claims.getAudience()).contains("spinnaker");
    assertThat(claims.getExpiresAt()).isAfter(Instant.now());
  }

  @Test
  void largeRoleSetRoundTripsAndStaysUnderHeaderLimit() {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");
    SpinnakerTokenSettings settings = settings();
    SpinnakerTokenMinter minter = new NimbusSpinnakerTokenMinter(key, settings);

    List<String> roles = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      roles.add("raven exclusion - perm_some_long_repetitive_role_name_" + i);
    }

    String token = minter.mint("steven@example.com", roles, true, false);

    // Compression must keep the propagated header well under Tomcat's 8KB max-http-header-size.
    assertThat(token.length()).isLessThan(8192);

    SpinnakerTokenClaims claims = verifierFor(settings, key).verify(token);
    assertThat(claims.getRoles()).isEqualTo(roles);
    assertThat(claims.isAdmin()).isTrue();
  }

  @Test
  void verifyFailsWhenRolesClaimIsNotValidCompressedData() throws Exception {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");
    SpinnakerTokenSettings settings = settings();

    // A properly signed token whose roles claim is a string that is not valid compressed data.
    Instant now = Instant.now();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject("mallory")
            .issuer(settings.getIssuer())
            .audience(settings.getAudience())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .claim(SpinnakerTokenClaims.CLAIM_ROLES, "not-valid-compressed-data")
            .build();
    SignedJWT signed =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claimsSet);
    signed.sign(new RSASSASigner(key));

    assertThatThrownBy(() -> verifierFor(settings, key).verify(signed.serialize()))
        .isInstanceOf(TokenValidationException.class);
  }

  @Test
  void verifyFailsWithWrongSigningKey() {
    RSAKey signingKey = IdentityTokenKeys.generateRsaKey("k1");
    RSAKey otherKey = IdentityTokenKeys.generateRsaKey("k2");
    SpinnakerTokenSettings settings = settings();

    String token =
        new NimbusSpinnakerTokenMinter(signingKey, settings)
            .mint("bob", List.of("dev"), false, false);

    assertThatThrownBy(() -> verifierFor(settings, otherKey).verify(token))
        .isInstanceOf(TokenValidationException.class);
  }

  @Test
  void verifyToleratesKeyRotationWindow() {
    RSAKey oldKey = IdentityTokenKeys.generateRsaKey("old");
    RSAKey newKey = IdentityTokenKeys.generateRsaKey("new");
    SpinnakerTokenSettings settings = settings();

    // token minted with the old key, verifier knows both keys (overlapping rotation window)
    String token =
        new NimbusSpinnakerTokenMinter(oldKey, settings).mint("carol", List.of("dev"), false, true);

    SpinnakerTokenClaims claims = verifierFor(settings, oldKey, newKey).verify(token);
    assertThat(claims.getSubject()).isEqualTo("carol");
    assertThat(claims.isAccountManager()).isTrue();
  }

  @Test
  void verifyFailsForExpiredToken() {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");
    SpinnakerTokenSettings settings = settings();
    settings.setValidity(Duration.ofMinutes(1));
    settings.setClockSkew(Duration.ofSeconds(1));

    // mint far in the past so it is expired well beyond the clock skew
    Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    String token =
        new NimbusSpinnakerTokenMinter(key, settings, past)
            .mint("dave", List.of("dev"), false, false);

    assertThatThrownBy(() -> verifierFor(settings, key).verify(token))
        .isInstanceOf(TokenValidationException.class);
  }

  @Test
  void verifyFailsForWrongIssuer() {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");
    SpinnakerTokenSettings minterSettings = settings();
    minterSettings.setIssuer("evil");
    String token =
        new NimbusSpinnakerTokenMinter(key, minterSettings)
            .mint("eve", List.of("dev"), false, false);

    SpinnakerTokenSettings verifierSettings = settings(); // issuer = "gate"
    assertThatThrownBy(() -> verifierFor(verifierSettings, key).verify(token))
        .isInstanceOf(TokenValidationException.class);
  }

  @Test
  void publicJwkSetStripsPrivateKeyMaterial() {
    RSAKey key = IdentityTokenKeys.generateRsaKey("k1");
    JWKSet publicSet = IdentityTokenKeys.publicJwkSet(key);
    assertThat(publicSet.getKeys()).hasSize(1);
    assertThat(publicSet.getKeys().get(0).isPrivate()).isFalse();
  }
}
