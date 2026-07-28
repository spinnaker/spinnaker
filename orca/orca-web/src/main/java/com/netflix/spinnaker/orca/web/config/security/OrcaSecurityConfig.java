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

package com.netflix.spinnaker.orca.web.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.config.AuthzPolicyProperties;
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeFilter;
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeProperties;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationFilter;
import com.netflix.spinnaker.security.authz.pdp.LegacyPermissionsPolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.IdentityTokenKeys;
import com.netflix.spinnaker.security.token.IdentityTokenVerifierProperties;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.SpinnakerTokenSettings;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Wires Orca's verifier-only, token-carried security chain (Component 1/3/6 of the RBAC
 * modernization).
 *
 * <p>Orca does not mint tokens — it verifies tokens that Gate (interactive logins) and Front50's
 * run-as endpoint minted, selecting the right public key from their published JWKS endpoints. The
 * chain:
 *
 * <ul>
 *   <li>Builds a {@link SpinnakerTokenVerifier} over a {@link JWKSource} that aggregates the
 *       configured JWKS endpoints (Gate + Front50 {@code /auth/jwks}).
 *   <li>Installs an {@link IdentityTokenAuthenticationFilter} ahead of the anonymous filter so the
 *       Spring {@code SecurityContext} carries the caller's identity (from the verified token) for
 *       audit and {@code @SpinnakerUser} resolution. Whether that identity is <em>enforced</em> is
 *       governed by {@code authz.enabled}: when disabled ({@code false}, the default) every {@code
 *       hasPermission} check is allow-all (full passthrough — the identity is not used to gate
 *       access); when enabled, decisions use the verified token roles and a missing or invalid
 *       token yields an anonymous, fail-closed context.
 *   <li>Exposes a single {@code PermissionEvaluator} bean named {@code
 *       spinnakerPermissionEvaluator} (the {@link OrcaPermissionEvaluator}) that {@code
 *       TaskController}'s {@code @PreAuthorize}/{@code @PostFilter} SpEL binds to. Method security
 *       ({@code @EnableGlobalMethodSecurity}) auto-detects this single evaluator.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({
  AuthorizationProperties.class,
  SpinnakerTokenSettings.class,
  AuthzPolicyProperties.class,
  IdentityTokenVerifierProperties.class,
  ApiTokenExchangeProperties.class
})
public class OrcaSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(OrcaSecurityConfig.class);

  @Bean
  public PolicyDecisionPoint policyDecisionPoint(AuthzPolicyProperties properties) {
    if (LegacyPermissionsPolicyDecisionPoint.PROVIDER_ID.equalsIgnoreCase(
        properties.getProvider())) {
      log.info("Orca authorization using legacy permissions PolicyDecisionPoint (fallback)");
      return new LegacyPermissionsPolicyDecisionPoint();
    }
    log.info("Orca authorization using Spring ACL PolicyDecisionPoint (default)");
    return new SpringAclPolicyDecisionPoint();
  }

  /**
   * The ACL resolver the {@code hasPermission(...)} by-id path consults. Orca's application checks
   * gate its own execution data, so they must evaluate against Front50's real ACLs; Front50 is
   * injected lazily so Orca still starts when Front50 is disabled.
   */
  @Bean
  public ResourceAclResolver orcaResourceAclResolver(
      ObjectProvider<Front50Service> front50Service, ObjectMapper objectMapper) {
    return new Front50ApplicationAclResolver(front50Service.getIfAvailable(), objectMapper);
  }

  /**
   * The Spring {@code PermissionEvaluator} that {@code hasPermission(...)} binds to, registered
   * under the SpEL bean name {@code spinnakerPermissionEvaluator} that {@code
   * @spinnakerPermissionEvaluator.*} references resolve against.
   */
  @Bean(name = "spinnakerPermissionEvaluator")
  public OrcaPermissionEvaluator spinnakerPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      ResourceAclResolver orcaResourceAclResolver,
      AuthzPolicyProperties properties,
      AuthorizationProperties authz) {
    return new OrcaPermissionEvaluator(
        policyDecisionPoint,
        orcaResourceAclResolver,
        authz.isEnabled(),
        properties.isAllowAccessToUnknownApplications());
  }

  /**
   * Trusts the minters' JWKS endpoints (Gate + Front50 run-as), derived from {@code
   * services.gate.baseUrl} / {@code services.front50.baseUrl} (the service URLs Orca is already
   * configured with) by appending {@code /auth/jwks}. When neither can be resolved the source is
   * empty and, in permissive mode, tokens fail verification and requests fall back to unsigned
   * identity headers.
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
  public SpinnakerTokenVerifier spinnakerTokenVerifier(
      JWKSource<SecurityContext> identityTokenKeySource, SpinnakerTokenSettings settings) {
    return new NimbusSpinnakerTokenVerifier(identityTokenKeySource, settings);
  }

  @Bean
  public AuthenticationConverter identityTokenAuthenticationConverter(
      SpinnakerTokenVerifier spinnakerTokenVerifier, AuthorizationProperties authz) {
    return new IdentityTokenAuthenticationConverter(spinnakerTokenVerifier, authz);
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
   * URL-level access is left open — authorization is enforced at the method level by
   * {@code @PreAuthorize}/{@code @PostFilter}.
   */
  @Bean
  public SecurityFilterChain orcaSecurityFilterChain(
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
