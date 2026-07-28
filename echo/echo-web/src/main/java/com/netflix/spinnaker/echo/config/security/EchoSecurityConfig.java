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

package com.netflix.spinnaker.echo.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.config.AuthzPolicyConfiguration;
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeFilter;
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeProperties;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationFilter;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.IdentityTokenKeys;
import com.netflix.spinnaker.security.token.IdentityTokenVerifierProperties;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.SpinnakerTokenSettings;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Wires Echo's verifier-only authorization chain. Echo never mints identity tokens — it obtains
 * short-lived run-as tokens from Front50's mint endpoint (see {@code RunAsTokenService}). It holds
 * no {@code authz.signing} key; it proves its identity to Front50's initial run-as mint via
 * service-to-service authentication ({@code authz.s2s}, which must be enabled), not by signing an
 * assertion. Because it has no identity-token minting code path, it cannot forge user tokens. For
 * inbound traffic it only verifies tokens.
 *
 * <ul>
 *   <li>Inbound identity-token verification ({@link IdentityTokenAuthenticationFilter}) installed
 *       ahead of the anonymous filter, populating the {@code SecurityContext} from the caller's
 *       verified identity token for audit and {@code @SpinnakerUser} resolution. Whether that
 *       identity is <em>enforced</em> is governed by {@code authz.enabled}: when disabled ({@code
 *       false}, the default) every {@code hasPermission} check is allow-all (full passthrough — the
 *       identity is not used to gate access); when enabled, decisions use the verified token roles
 *       and a missing or invalid token yields an anonymous, fail-closed context.
 *   <li>A {@link JWKSource} that trusts the configured minters' JWKS endpoints — Gate's
 *       (interactive users) and Front50's run-as JWKS ({@code GET /auth/jwks}).
 *   <li>The shared {@link AuthzPolicyConfiguration} PDP/{@code PermissionEvaluator}, re-exposed
 *       under the bean name {@code spinnakerPermissionEvaluator} that any
 *       {@code @PreAuthorize}/{@code hasPermission} SpEL binds to.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({
  AuthorizationProperties.class,
  SpinnakerTokenSettings.class,
  IdentityTokenVerifierProperties.class,
  ApiTokenExchangeProperties.class
})
@Import(AuthzPolicyConfiguration.class)
public class EchoSecurityConfig {

  /**
   * Re-exposes the shared {@link PolicyDecisionPointPermissionEvaluator} under the SpEL bean name
   * {@code spinnakerPermissionEvaluator} that {@code @spinnakerPermissionEvaluator.*} references
   * resolve against. Echo currently performs no method-level {@code hasPermission} checks (pipeline
   * EXECUTE authorization moved to the Front50 boundary), but the bean is registered for parity
   * with the other migrated services.
   */
  @Bean(name = "spinnakerPermissionEvaluator")
  public PermissionEvaluator spinnakerPermissionEvaluator(
      PolicyDecisionPointPermissionEvaluator permissionEvaluator) {
    return permissionEvaluator;
  }

  /**
   * Trusts the minters' JWKS endpoints (Gate + Front50 run-as), derived from {@code
   * services.gate.baseUrl} / {@code services.front50.baseUrl} (the service URLs Echo is already
   * configured with) by appending {@code /auth/jwks}. When neither can be resolved no token
   * verifies, and permissive mode falls back to the unsigned identity headers.
   */
  @Bean
  public JWKSource<SecurityContext> identityTokenKeySource(
      IdentityTokenVerifierProperties properties,
      AuthorizationProperties authz,
      @Value("${services.gate.baseUrl:}") String gateBaseUrl,
      @Value("${services.front50.baseUrl:}") String front50BaseUrl) {
    return IdentityTokenKeys.verificationKeySource(
        properties, authz.isEnabled(), List.of(gateBaseUrl, front50BaseUrl));
  }

  @Bean
  public SpinnakerTokenVerifier identityTokenVerifier(
      JWKSource<SecurityContext> identityTokenKeySource, SpinnakerTokenSettings settings) {
    return new NimbusSpinnakerTokenVerifier(identityTokenKeySource, settings);
  }

  @Bean
  public AuthenticationConverter identityTokenAuthenticationConverter(
      SpinnakerTokenVerifier identityTokenVerifier, AuthorizationProperties authz) {
    return new IdentityTokenAuthenticationConverter(identityTokenVerifier, authz);
  }

  /**
   * An empty {@link UserDetailsService} so Spring Boot's {@code
   * UserDetailsServiceAutoConfiguration} backs off and never creates its default {@code user} (nor
   * logs the "Using generated security password" line). This chain is token-only and performs no
   * username/password authentication, so the manager holds no users and is never consulted.
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return new InMemoryUserDetailsManager();
  }

  /**
   * Installs the {@link IdentityTokenAuthenticationFilter} ahead of the anonymous filter. When
   * direct API-token support is enabled, an {@link ApiTokenExchangeFilter} runs just before it to
   * swap an opaque {@code spk_} token for the signed identity token Gate would have minted.
   * URL-level access is left open (Echo's authorization, where present, is enforced at the method
   * level / at the owning service); the filter only establishes the verified identity on the
   * request.
   */
  @Bean
  public SecurityFilterChain echoSecurityFilterChain(
      HttpSecurity http,
      AuthenticationConverter identityTokenAuthenticationConverter,
      ApiTokenExchangeProperties apiTokenExchangeProperties,
      @Value("${services.gate.baseUrl:}") String gateBaseUrl)
      throws Exception {
    ApiTokenExchangeFilter apiTokenExchangeFilter =
        ApiTokenExchangeFilter.createIfEnabled(apiTokenExchangeProperties, gateBaseUrl);
    http.csrf(AbstractHttpConfigurer::disable)
        .servletApi(withDefaults())
        .exceptionHandling(withDefaults())
        .anonymous(withDefaults())
        .addFilterBefore(
            new IdentityTokenAuthenticationFilter(identityTokenAuthenticationConverter),
            AnonymousAuthenticationFilter.class);
    if (apiTokenExchangeFilter != null) {
      http.addFilterBefore(apiTokenExchangeFilter, IdentityTokenAuthenticationFilter.class);
    }
    return http.build();
  }
}
