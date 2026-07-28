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

import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter;
import com.netflix.spinnaker.security.roles.UserRolesResolver;
import com.netflix.spinnaker.security.roles.config.RoleResolutionConfiguration;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.IdentityTokenKeys;
import com.netflix.spinnaker.security.token.IdentityTokenSigningKeys;
import com.netflix.spinnaker.security.token.IdentityTokenSigningProperties;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenMinter;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.SpinnakerTokenMinter;
import com.netflix.spinnaker.security.token.SpinnakerTokenSettings;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires Gate's edge identity-token machinery: role resolution ({@code kork-roles}), token minting /
 * verification ({@code kork-security}), and the {@link GateIdentityService} facade.
 *
 * <p>Role sources stay pluggable (Component 9): the {@code kork-roles} providers (file/LDAP/GitHub
 * teams/Google groups) are component-scanned and activate via {@code
 * auth.group-membership.service}; {@link RoleResolutionConfiguration} then exposes a {@link
 * UserRolesResolver}. Authorization is gated by the {@code authz.enabled} master switch; token
 * mint/verify claims by {@code authz.token.*}; admin/account-manager role mapping by {@code
 * authz.gate.*}.
 */
@Configuration
@EnableConfigurationProperties({
  AuthorizationProperties.class,
  SpinnakerTokenSettings.class,
  GateAuthzProperties.class,
  IdentityTokenSigningProperties.class
})
@Import(RoleResolutionConfiguration.class)
@ComponentScan("com.netflix.spinnaker.security.roles")
public class IdentityTokenConfiguration {

  /**
   * The RSA signing keys used to mint identity tokens, resolved from the shared {@code
   * authz.signing} key set. The minter signs with the single active key while every configured
   * key's public half is published, enabling zero-downtime rotation. When authorization is enabled
   * ({@code authz.enabled=true}) startup fails fast if no key is configured. When authorization is
   * disabled and no key is configured there is no active key and Gate mints no identity tokens
   * (downstream falls back to unsigned headers).
   */
  @Bean
  public IdentityTokenSigningKeys identityTokenSigningKeys(
      IdentityTokenSigningProperties signing, AuthorizationProperties authz) {
    return IdentityTokenKeys.resolveSigningKeys(signing, authz.isEnabled());
  }

  /**
   * The token minter, or {@code null} when no signing key is configured (authorization disabled).
   * {@link GateIdentityService} tolerates an absent minter and simply mints no tokens.
   */
  @Bean
  public SpinnakerTokenMinter spinnakerTokenMinter(
      IdentityTokenSigningKeys identityTokenSigningKeys, SpinnakerTokenSettings settings) {
    if (identityTokenSigningKeys.getActive() == null) {
      return null;
    }
    return new NimbusSpinnakerTokenMinter(identityTokenSigningKeys.getActive(), settings);
  }

  /**
   * The public JWK set (private key material stripped) that Gate publishes so downstream services
   * can verify the interactive-user identity tokens it mints. Served over HTTP by {@link
   * IdentityTokenJwksController} at {@code GET /auth/jwks}; downstream verifiers reach that
   * endpoint by deriving it from {@code services.gate.baseUrl}. All configured keys (active plus
   * any inside a rotation overlap window) are published.
   */
  @Bean
  public JWKSet identityTokenPublicJwks(IdentityTokenSigningKeys identityTokenSigningKeys) {
    return identityTokenSigningKeys.publicJwkSet();
  }

  @Bean
  public JWKSource<SecurityContext> identityTokenKeySource(JWKSet identityTokenPublicJwks) {
    return IdentityTokenKeys.immutableKeySource(identityTokenPublicJwks);
  }

  @Bean
  public SpinnakerTokenVerifier spinnakerTokenVerifier(
      JWKSource<SecurityContext> identityTokenKeySource, SpinnakerTokenSettings settings) {
    return new NimbusSpinnakerTokenVerifier(identityTokenKeySource, settings);
  }

  @Bean
  public IdentityTokenAuthenticationConverter identityTokenAuthenticationConverter(
      SpinnakerTokenVerifier verifier, AuthorizationProperties authz) {
    return new IdentityTokenAuthenticationConverter(verifier, authz);
  }

  @Bean
  public GateIdentityService gateIdentityService(
      ObjectProvider<UserRolesResolver> userRolesResolver,
      ObjectProvider<SpinnakerTokenMinter> tokenMinter,
      GateAuthzProperties properties) {
    return new GateIdentityService(
        userRolesResolver.getIfAvailable(), tokenMinter.getIfAvailable(), properties);
  }
}
