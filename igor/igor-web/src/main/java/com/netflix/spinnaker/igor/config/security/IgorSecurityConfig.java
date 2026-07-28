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

package com.netflix.spinnaker.igor.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import com.netflix.spinnaker.igor.service.BuildServices;
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
import java.util.Optional;
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
 * Wires Igor's owner-local authorization with a <em>verifier-only</em> identity-token chain — Igor
 * never mints tokens (it holds no signing key), it only verifies tokens minted by Gate (interactive
 * users) and Front50 (run-as), so there is no minter/run-as endpoint here.
 *
 * <ul>
 *   <li>Method security ({@code @PreAuthorize}/{@code @PostFilter}) backed by a single {@code
 *       PermissionEvaluator} bean named {@code spinnakerPermissionEvaluator} (the {@link
 *       IgorPermissionEvaluator}), which the annotations bind to.
 *   <li>A {@link PolicyDecisionPoint} (Spring ACL by default, legacy-permissions fallback
 *       selectable via {@code authz.pdp.provider}).
 *   <li>An owner-local {@link ResourceAclResolver} reading Igor's own build-service ACLs.
 *   <li>Inbound identity-token verification ({@link IdentityTokenAuthenticationFilter}) against the
 *       trusted minters' JWKS endpoints (derived from {@code services.gate.baseUrl} / {@code
 *       services.front50.baseUrl}). When authorization is disabled ({@code authz.enabled=false},
 *       the default) method-level enforcement is bypassed (every {@code hasPermission} check
 *       allows).
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({
  AuthorizationProperties.class,
  SpinnakerTokenSettings.class,
  AuthzPolicyProperties.class,
  IgorAuthzProperties.class,
  IdentityTokenVerifierProperties.class,
  ApiTokenExchangeProperties.class
})
public class IgorSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(IgorSecurityConfig.class);

  @Bean
  public PolicyDecisionPoint policyDecisionPoint(AuthzPolicyProperties properties) {
    if (LegacyPermissionsPolicyDecisionPoint.PROVIDER_ID.equalsIgnoreCase(
        properties.getProvider())) {
      log.info("Igor authorization using legacy permissions PolicyDecisionPoint (fallback)");
      return new LegacyPermissionsPolicyDecisionPoint();
    }
    log.info("Igor authorization using Spring ACL PolicyDecisionPoint (default)");
    return new SpringAclPolicyDecisionPoint();
  }

  @Bean
  public ResourceAclResolver igorResourceAclResolver(
      BuildServices buildServices,
      ObjectProvider<GoogleCloudBuildProperties> googleCloudBuildProperties) {
    return new IgorResourceAclResolver(
        buildServices, Optional.ofNullable(googleCloudBuildProperties.getIfAvailable()));
  }

  /**
   * The Spring {@code PermissionEvaluator} that {@code hasPermission(...)} binds to, registered
   * under the SpEL bean name {@code spinnakerPermissionEvaluator} that any {@code
   * @spinnakerPermissionEvaluator.*} references resolve against.
   */
  @Bean(name = "spinnakerPermissionEvaluator")
  public IgorPermissionEvaluator spinnakerPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      ResourceAclResolver igorResourceAclResolver,
      IgorAuthzProperties properties) {
    return new IgorPermissionEvaluator(
        policyDecisionPoint,
        igorResourceAclResolver,
        properties.isAllowAccessToUnknownBuildServices());
  }

  /**
   * The verifier key source. Igor trusts the public keys published by each minter's JWKS endpoint
   * (Gate and Front50's run-as), derived from {@code services.gate.baseUrl} / {@code
   * services.front50.baseUrl} (the service URLs Igor is already configured with) by appending
   * {@code /auth/jwks}. When neither can be resolved the source is empty, which (in permissive
   * mode) means token verification simply fails and Igor falls back to the legacy unsigned identity
   * headers.
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
  public SecurityFilterChain igorSecurityFilterChain(
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
