/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.front50.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import com.netflix.spinnaker.security.authz.config.AuthzPolicyProperties;
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeFilter;
import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeProperties;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationFilter;
import com.netflix.spinnaker.security.authz.pdp.LegacyPermissionsPolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import com.netflix.spinnaker.security.s2s.config.ServiceToServiceAuthConfiguration;
import com.netflix.spinnaker.security.s2s.filter.ServiceCallerAuthenticationFilter;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.IdentityTokenKeys;
import com.netflix.spinnaker.security.token.IdentityTokenSigningKeys;
import com.netflix.spinnaker.security.token.IdentityTokenSigningProperties;
import com.netflix.spinnaker.security.token.IdentityTokenVerifierProperties;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenMinter;
import com.netflix.spinnaker.security.token.NimbusSpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.SpinnakerTokenMinter;
import com.netflix.spinnaker.security.token.SpinnakerTokenSettings;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
 * Wires Front50's owner-local authorization:
 *
 * <ul>
 *   <li>Method security ({@code @PreAuthorize}/{@code @PostFilter}) backed by a single {@code
 *       PermissionEvaluator} bean named {@code spinnakerPermissionEvaluator} (the {@link
 *       Front50PermissionEvaluator}), which the annotations bind to.
 *   <li>A {@link PolicyDecisionPoint} (Spring ACL by default, legacy-permissions fallback
 *       selectable via {@code authz.pdp.provider}).
 *   <li>An owner-local {@link ResourceAclResolver} reading Front50's own application + service
 *       account ACLs.
 *   <li>Inbound identity-token verification ({@link IdentityTokenAuthenticationFilter}); when
 *       authorization is disabled ({@code authz.enabled} default off) enforcement is bypassed.
 *   <li>The run-as token minter, which signs with the shared {@code authz.signing} key set (the
 *       same key Gate signs with — run-as and interactive tokens differ by claims, not signature).
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Import(ServiceToServiceAuthConfiguration.class)
@EnableConfigurationProperties({
  AuthorizationProperties.class,
  SpinnakerTokenSettings.class,
  AuthzPolicyProperties.class,
  ApplicationDefaultPermissionsProperties.class,
  RunAsTokenProperties.class,
  IdentityTokenSigningProperties.class,
  IdentityTokenVerifierProperties.class,
  ApiTokenExchangeProperties.class
})
public class Front50SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(Front50SecurityConfig.class);

  @Bean
  public PolicyDecisionPoint policyDecisionPoint(AuthzPolicyProperties properties) {
    if (LegacyPermissionsPolicyDecisionPoint.PROVIDER_ID.equalsIgnoreCase(
        properties.getProvider())) {
      log.info("Front50 authorization using legacy permissions PolicyDecisionPoint (fallback)");
      return new LegacyPermissionsPolicyDecisionPoint();
    }
    log.info("Front50 authorization using Spring ACL PolicyDecisionPoint (default)");
    return new SpringAclPolicyDecisionPoint();
  }

  @Bean
  public ResourceAclResolver front50ResourceAclResolver(
      Optional<ApplicationPermissionDAO> applicationPermissionDAO,
      Optional<ServiceAccountDAO> serviceAccountDAO,
      ApplicationDefaultPermissionsProperties applicationDefaultPermissions) {
    return new Front50ResourceAclResolver(
        applicationPermissionDAO, serviceAccountDAO, applicationDefaultPermissions);
  }

  /**
   * The Spring {@code PermissionEvaluator} that {@code hasPermission(...)} binds to, registered
   * under the SpEL bean name {@code spinnakerPermissionEvaluator} that {@code
   * @spinnakerPermissionEvaluator.*} references resolve against.
   */
  @Bean(name = "spinnakerPermissionEvaluator")
  public Front50PermissionEvaluator spinnakerPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      ResourceAclResolver front50ResourceAclResolver,
      AuthzPolicyProperties properties,
      AuthorizationProperties authz,
      ApplicationDefaultPermissionsProperties applicationDefaultPermissions) {
    return new Front50PermissionEvaluator(
        policyDecisionPoint,
        front50ResourceAclResolver,
        authz.isEnabled(),
        properties.isAllowAccessToUnknownApplications(),
        applicationDefaultPermissions);
  }

  /**
   * Front50's run-as signing keys, resolved from the shared {@code authz.signing} key set — the
   * same key(s) Gate signs with, so a single key can be shared across both minters. When
   * authorization is enabled ({@code authz.enabled=true}) startup fails fast if no key is
   * configured rather than minting tokens no verifier trusts. When authorization is disabled and no
   * key is configured there is no active key and Front50 mints no run-as tokens.
   */
  @Bean
  public IdentityTokenSigningKeys runAsSigningKeys(
      IdentityTokenSigningProperties signing, AuthorizationProperties authz) {
    return IdentityTokenKeys.resolveSigningKeys(signing, authz.isEnabled());
  }

  /**
   * The run-as token minter, or {@code null} when no signing key is configured (authorization
   * disabled). {@link com.netflix.spinnaker.front50.controllers.RunAsTokenController} tolerates an
   * absent minter and rejects mint requests rather than minting unverifiable tokens.
   */
  @Bean
  public SpinnakerTokenMinter runAsTokenMinter(
      IdentityTokenSigningKeys runAsSigningKeys, SpinnakerTokenSettings settings) {
    if (runAsSigningKeys.getActive() == null) {
      return null;
    }
    return new NimbusSpinnakerTokenMinter(runAsSigningKeys.getActive(), settings);
  }

  /**
   * The public JWK set Front50 publishes so verifiers can validate its run-as tokens. All
   * configured keys (active plus any inside a rotation overlap window) are published.
   */
  @Bean
  public JWKSet runAsPublicJwks(IdentityTokenSigningKeys runAsSigningKeys) {
    return runAsSigningKeys.publicJwkSet();
  }

  /**
   * Front50's inbound-token verifier trusts Gate's interactive-user JWKS — derived from {@code
   * services.gate.baseUrl} by appending {@code /auth/jwks} — <em>plus</em> its own published run-as
   * signing key (contributed locally, so Front50 verifies the run-as tokens it mints without
   * fetching its own endpoint). Because it always contributes a local source it never trips the
   * strict-mode empty-source guard.
   */
  @Bean
  public JWKSource<SecurityContext> identityTokenKeySource(
      IdentityTokenVerifierProperties properties,
      JWKSet runAsPublicJwks,
      AuthorizationProperties authz,
      @Value("${services.gate.baseUrl:}") String gateBaseUrl) {
    return IdentityTokenKeys.verificationKeySource(
        properties,
        authz.isEnabled(),
        List.of(gateBaseUrl),
        IdentityTokenKeys.immutableKeySource(runAsPublicJwks));
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
   * Installs the {@link IdentityTokenAuthenticationFilter} ahead of the anonymous filter so the
   * {@code SecurityContext} is populated from the verified inbound token (falling back to unsigned
   * headers in permissive mode). When direct API-token support is enabled, an {@link
   * ApiTokenExchangeFilter} runs just before it to swap an opaque {@code spk_} token for the signed
   * identity token Gate would have minted. The {@link ServiceCallerAuthenticationFilter} is wired
   * explicitly (rather than relying on Spring Boot's implicit orphan-filter registration, which
   * does not guarantee it runs inside Spring Security's {@code FilterChainProxy}) so {@code
   * ServiceCallerContext} is populated before authorization runs. URL-level access is left open —
   * authorization is enforced at the method level by {@code @PreAuthorize}/{@code @PostFilter}.
   */
  @Bean
  public SecurityFilterChain front50SecurityFilterChain(
      HttpSecurity http,
      AuthenticationConverter identityTokenAuthenticationConverter,
      ServiceCallerAuthenticationFilter serviceCallerAuthenticationFilter,
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
            AnonymousAuthenticationFilter.class)
        .addFilterBefore(
            serviceCallerAuthenticationFilter, IdentityTokenAuthenticationFilter.class);
    if (apiTokenExchangeFilter != null) {
      http.addFilterBefore(apiTokenExchangeFilter, IdentityTokenAuthenticationFilter.class);
    }
    return http.build();
  }

  /**
   * Prevent Boot from auto-registering the {@link ServiceCallerAuthenticationFilter} bean as a
   * standalone servlet filter; it's already wired into {@link #front50SecurityFilterChain} and
   * would otherwise run twice (and at an unverified position relative to Spring Security's {@code
   * FilterChainProxy}).
   */
  @Bean
  public FilterRegistrationBean<ServiceCallerAuthenticationFilter>
      serviceCallerAuthenticationFilterRegistration(ServiceCallerAuthenticationFilter filter) {
    FilterRegistrationBean<ServiceCallerAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
