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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.annotation.Nonnull;

/**
 * {@link SpinnakerTokenMinter} backed by nimbus-jose-jwt, signing with an RSA private key (RS256).
 *
 * <p>The minter stamps {@code iss}/{@code aud}/{@code iat}/{@code exp} from its {@link
 * SpinnakerTokenSettings} and embeds the subject, roles, and admin/account-manager flags as claims.
 */
public class NimbusSpinnakerTokenMinter implements SpinnakerTokenMinter {

  private final RSASSASigner signer;
  private final String keyId;
  private final SpinnakerTokenSettings settings;
  private final Clock clock;

  public NimbusSpinnakerTokenMinter(RSAKey signingKey, SpinnakerTokenSettings settings) {
    this(signingKey, settings, Clock.systemUTC());
  }

  public NimbusSpinnakerTokenMinter(
      RSAKey signingKey, SpinnakerTokenSettings settings, Clock clock) {
    try {
      this.signer = new RSASSASigner(signingKey);
    } catch (JOSEException e) {
      throw new IllegalArgumentException("Signing key is not a usable RSA private key", e);
    }
    this.keyId = signingKey.getKeyID();
    this.settings = settings;
    this.clock = clock;
  }

  @Nonnull
  @Override
  public String mint(@Nonnull SpinnakerTokenClaims claims) {
    Instant now = clock.instant();
    Instant expiry = now.plus(settings.getValidity());

    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder()
            .subject(claims.getSubject())
            .issuer(settings.getIssuer())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiry))
            .claim(SpinnakerTokenClaims.CLAIM_ROLES, RoleClaimCodec.encode(claims.getRoles()))
            .claim(SpinnakerTokenClaims.CLAIM_ADMIN, claims.isAdmin())
            .claim(SpinnakerTokenClaims.CLAIM_ACCOUNT_MANAGER, claims.isAccountManager());

    if (settings.getAudience() != null && !settings.getAudience().isEmpty()) {
      builder.audience(settings.getAudience());
    }

    SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), builder.build());
    try {
      signedJWT.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to sign identity token", e);
    }
    return signedJWT.serialize();
  }
}
