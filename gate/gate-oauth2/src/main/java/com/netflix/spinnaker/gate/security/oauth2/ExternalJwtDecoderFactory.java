/*
 * Copyright 2026 Spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Builds the {@link JwtDecoder} that {@link ExternalAuthTokenFilter} uses to validate JWT bearer
 * tokens locally: signature against the issuer's keys, expiry and not-before, issuer, audience and
 * optionally scopes.
 */
final class ExternalJwtDecoderFactory {

  private ExternalJwtDecoderFactory() {}

  /**
   * Creates the decoder for the given settings. The signing keys (and, without a {@code
   * jwk-set-uri}, the issuer metadata) are only fetched on the first token, so Gate starts even
   * when the issuer is unreachable.
   *
   * @throws IllegalStateException if JWT validation is enabled without any audience
   */
  static JwtDecoder create(ExternalAuthTokenFilterConfigurationProperties.Jwt properties) {
    OAuth2TokenValidator<Jwt> validator = validator(properties);
    String jwkSetUri = properties.getJwkSetUri();
    return new SupplierJwtDecoder(
        () -> {
          NimbusJwtDecoder decoder =
              (jwkSetUri != null && !jwkSetUri.isBlank())
                  ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                  : NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri()).build();
          decoder.setJwtValidator(validator);
          return decoder;
        });
  }

  /** The checks a decoded token must pass, in addition to its signature. */
  static OAuth2TokenValidator<Jwt> validator(
      ExternalAuthTokenFilterConfigurationProperties.Jwt properties) {
    List<String> audiences = properties.getAudiences();
    if (audiences == null || audiences.isEmpty()) {
      throw new IllegalStateException(
          "security.oauth2.external-auth-token-filter.jwt.audiences must be set when "
              + "security.oauth2.external-auth-token-filter.jwt.issuer-uri is set");
    }

    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    // Timestamps (exp, nbf) and an exact match of the iss claim.
    validators.add(JwtValidators.createDefaultWithIssuer(properties.getIssuerUri()));
    validators.add(
        new JwtClaimValidator<Collection<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(audiences::contains)));

    List<String> requiredScopes = properties.getRequiredScopes();
    if (requiredScopes != null && !requiredScopes.isEmpty()) {
      validators.add(new RequiredScopesValidator(requiredScopes));
    }
    return new DelegatingOAuth2TokenValidator<>(validators);
  }

  /** Requires every configured scope in the token's {@code scp} or {@code scope} claim. */
  static final class RequiredScopesValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> requiredScopes;

    RequiredScopesValidator(List<String> requiredScopes) {
      this.requiredScopes = List.copyOf(requiredScopes);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
      Set<String> granted = grantedScopes(token);
      if (granted.containsAll(requiredScopes)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "The token doesn't have all required scopes " + requiredScopes,
              null));
    }

    private static Set<String> grantedScopes(Jwt token) {
      Object claim = token.getClaims().get("scp");
      if (claim == null) {
        claim = token.getClaims().get("scope");
      }
      Set<String> scopes = new HashSet<>();
      if (claim instanceof String value) {
        scopes.addAll(Arrays.asList(value.trim().split("\\s+")));
      } else if (claim instanceof Collection<?> values) {
        values.forEach(value -> scopes.add(String.valueOf(value)));
      }
      return scopes;
    }
  }
}
