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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * {@link SpinnakerTokenVerifier} backed by nimbus-jose-jwt.
 *
 * <p>Verifies the RS256 signature against the public keys provided by the {@link JWKSource}
 * (selected by {@code kid}, supporting rotation), and validates {@code iss}/{@code aud}/{@code exp}
 * with the configured clock-skew tolerance.
 */
public class NimbusSpinnakerTokenVerifier implements SpinnakerTokenVerifier {

  private final DefaultJWTProcessor<SecurityContext> jwtProcessor;

  public NimbusSpinnakerTokenVerifier(
      JWKSource<SecurityContext> keySource, SpinnakerTokenSettings settings) {
    JWSVerificationKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
    int clockSkew = (int) settings.getClockSkew().getSeconds();

    JWTClaimsSet.Builder exactMatch = new JWTClaimsSet.Builder();
    if (settings.getIssuer() != null) {
      exactMatch.issuer(settings.getIssuer());
    }
    JWTClaimsSet exactMatchClaims = exactMatch.build();
    Set<String> acceptedAudience =
        settings.getAudience() == null
            ? Collections.emptySet()
            : new LinkedHashSet<>(settings.getAudience());

    // Enforces signature, iss/aud and (via DefaultJWTClaimsVerifier) the short exp.
    this.jwtProcessor = new DefaultJWTProcessor<>();
    this.jwtProcessor.setJWSKeySelector(keySelector);
    DefaultJWTClaimsVerifier<SecurityContext> useVerifier =
        acceptedAudience.isEmpty()
            ? new DefaultJWTClaimsVerifier<>(exactMatchClaims, Set.of("sub", "exp"))
            : new DefaultJWTClaimsVerifier<>(
                acceptedAudience, exactMatchClaims, Set.of("sub", "exp"), Collections.emptySet());
    useVerifier.setMaxClockSkew(clockSkew);
    this.jwtProcessor.setJWTClaimsSetVerifier(useVerifier);
  }

  @Nonnull
  @Override
  public SpinnakerTokenClaims verify(@Nonnull String serializedToken)
      throws TokenValidationException {
    return toClaims(process(jwtProcessor, serializedToken));
  }

  private static JWTClaimsSet process(
      DefaultJWTProcessor<SecurityContext> processor, String serializedToken)
      throws TokenValidationException {
    try {
      return processor.process(serializedToken, null);
    } catch (Exception e) {
      throw new TokenValidationException("Identity token verification failed", e);
    }
  }

  private static SpinnakerTokenClaims toClaims(JWTClaimsSet claimsSet)
      throws TokenValidationException {
    try {
      List<String> roles =
          RoleClaimCodec.decode(claimsSet.getStringClaim(SpinnakerTokenClaims.CLAIM_ROLES));
      Boolean admin = claimsSet.getBooleanClaim(SpinnakerTokenClaims.CLAIM_ADMIN);
      Boolean accountManager =
          claimsSet.getBooleanClaim(SpinnakerTokenClaims.CLAIM_ACCOUNT_MANAGER);
      Date issuedAt = claimsSet.getIssueTime();
      Date expiry = claimsSet.getExpirationTime();

      return SpinnakerTokenClaims.builder(claimsSet.getSubject())
          .roles(roles == null ? List.of() : roles)
          .admin(Boolean.TRUE.equals(admin))
          .accountManager(Boolean.TRUE.equals(accountManager))
          .issuer(claimsSet.getIssuer())
          .audience(claimsSet.getAudience())
          .issuedAt(issuedAt == null ? null : issuedAt.toInstant())
          .expiresAt(expiry == null ? null : expiry.toInstant())
          .build();
    } catch (ParseException | IllegalArgumentException e) {
      throw new TokenValidationException("Identity token has malformed claims", e);
    }
  }
}
