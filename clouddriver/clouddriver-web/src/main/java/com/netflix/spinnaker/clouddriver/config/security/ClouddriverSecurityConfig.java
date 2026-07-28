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

package com.netflix.spinnaker.clouddriver.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
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
 * Wires Clouddriver's verifier-only owner-local authorization chain. Clouddriver never mints
 * identity tokens (only Gate and Front50 hold signing keys); it only verifies inbound tokens and
 * enforces {@code account} ACLs from its own in-process credentials. Mirrors Front50's wiring minus
 * the minter:
 *
 * <ul>
 *   <li>Method security ({@code @PreAuthorize}/{@code @PostFilter}) backed by a single {@code
 *       PermissionEvaluator} bean named {@code spinnakerPermissionEvaluator} (the {@link
 *       ClouddriverPermissionEvaluator}), which the annotations bind to.
 *   <li>A {@link PolicyDecisionPoint} (Spring ACL by default, legacy-permissions fallback via
 *       {@code authz.pdp.provider}).
 *   <li>An owner-local {@link ResourceAclResolver} reading Clouddriver's own account ACLs.
 *   <li>Inbound identity-token verification ({@link IdentityTokenAuthenticationFilter}) against
 *       Gate's and Front50's published JWKS; when authorization is disabled ({@code authz.enabled}
 *       default off) enforcement is bypassed.
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
public class ClouddriverSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(ClouddriverSecurityConfig.class);

  @Bean
  public PolicyDecisionPoint policyDecisionPoint(AuthzPolicyProperties properties) {
    if (LegacyPermissionsPolicyDecisionPoint.PROVIDER_ID.equalsIgnoreCase(
        properties.getProvider())) {
      log.info("Clouddriver authorization using legacy permissions PolicyDecisionPoint (fallback)");
      return new LegacyPermissionsPolicyDecisionPoint();
    }
    log.info("Clouddriver authorization using Spring ACL PolicyDecisionPoint (default)");
    return new SpringAclPolicyDecisionPoint();
  }

  /**
   * The ACL resolver the {@code hasPermission(...)} by-id path consults. Composes Clouddriver's
   * in-process {@code account} resolver ({@link ClouddriverResourceAclResolver}) with the
   * Front50-backed {@code application} resolver ({@link Front50ApplicationAclResolver}), so both
   * account and application checks evaluate against real ACLs. Front50 is injected lazily so
   * Clouddriver still starts (and account authz still works) when Front50 is disabled.
   */
  @Bean
  public ResourceAclResolver clouddriverResourceAclResolver(
      AccountCredentialsProvider accountCredentialsProvider,
      ObjectProvider<Front50Service> front50Service,
      ObjectMapper objectMapper) {
    ResourceAclResolver accounts = new ClouddriverResourceAclResolver(accountCredentialsProvider);
    ResourceAclResolver applications =
        new Front50ApplicationAclResolver(front50Service.getIfAvailable(), objectMapper);
    return new CompositeResourceAclResolver(List.of(accounts, applications));
  }

  /**
   * The Spring {@code PermissionEvaluator} that {@code hasPermission(...)} binds to, registered
   * under the SpEL bean name {@code spinnakerPermissionEvaluator} that {@code
   * @spinnakerPermissionEvaluator.*} references resolve against.
   */
  @Bean(name = "spinnakerPermissionEvaluator")
  public ClouddriverPermissionEvaluator spinnakerPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      ResourceAclResolver clouddriverResourceAclResolver,
      AuthzPolicyProperties properties,
      AuthorizationProperties authz) {
    return new ClouddriverPermissionEvaluator(
        policyDecisionPoint,
        clouddriverResourceAclResolver,
        authz.isEnabled(),
        properties.isAllowAccessToUnknownApplications());
  }

  /**
   * The JWKS source the inbound-token verifier consults: the trusted minters' public keys (Gate +
   * Front50 run-as), derived from {@code services.gate.baseUrl} / {@code services.front50.baseUrl}
   * (the service URLs Clouddriver is already configured with) by appending {@code /auth/jwks}. Each
   * endpoint is fetched and cached (and refreshed on rotation). When neither can be resolved the
   * source is empty, so in permissive mode every token fails verification and the request falls
   * back to unsigned identity headers.
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
   * Installs the {@link IdentityTokenAuthenticationFilter} ahead of the anonymous filter so the
   * {@code SecurityContext} is populated from the verified inbound token (falling back to unsigned
   * headers in permissive mode). When direct API-token support is enabled, an {@link
   * ApiTokenExchangeFilter} runs just before it to swap an opaque {@code spk_} token for the signed
   * identity token Gate would have minted. URL-level access is left open — authorization is
   * enforced at the method level by {@code @PreAuthorize}/{@code @PostFilter}.
   */
  @Bean
  public SecurityFilterChain clouddriverSecurityFilterChain(
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
