/*
 * Copyright 2016 Netflix, Inc.
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.config;

import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.gate.security.token.GateIdentityTokenInboundFilter;
import com.netflix.spinnaker.gate.security.token.IdentityTokenPropagationFilter;
import com.netflix.spinnaker.gate.services.ServiceAccountFilterConfigProps;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({ServiceConfiguration.class, DynamicRoutingConfigProperties.class})
@NonnullByDefault
@RequiredArgsConstructor
public class AuthConfig {

  /**
   * Resolves the service-account filter config, preferring the canonical {@code
   * authz.service-accounts.filter} key and falling back to the deprecated {@code
   * fiat.service-accounts.filter} alias. Registered as an explicit bean (rather than via
   * {@code @EnableConfigurationProperties}) so the dual-prefix/deprecation-fallback logic in {@link
   * ServiceAccountFilterConfigProps#bind} can run.
   */
  @Bean
  public ServiceAccountFilterConfigProps serviceAccountFilterConfigProps(Environment environment) {
    return ServiceAccountFilterConfigProps.bind(environment);
  }

  private final PermissionRevokingLogoutSuccessHandler permissionRevokingLogoutSuccessHandler;
  private final RequestMatcherProvider requestMatcherProvider;

  @Setter(
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${security.debug:false}")})
  private boolean securityDebug;

  @Setter(
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${security.webhooks.default-auth-enabled:false}")})
  private boolean webhookDefaultAuthEnabled;

  /** Edge identity facade; null when the identity-token machinery is not configured. */
  @Setter(onMethod_ = {@Autowired(required = false)})
  private GateIdentityService gateIdentityService;

  /** Verifies inbound identity tokens at the edge; null when not configured. */
  @Setter(onMethod_ = {@Autowired(required = false)})
  private IdentityTokenAuthenticationConverter identityTokenAuthenticationConverter;

  @Bean
  public WebSecurityCustomizer securityDebugCustomizer() {
    return web -> web.debug(securityDebug);
  }

  public void configure(HttpSecurity http) throws Exception {
    http.securityMatcher(requestMatcherProvider.requestMatcher())
        .authorizeHttpRequests(
            registry -> {
              registry
                  // https://github.com/spring-projects/spring-security/issues/11055#issuecomment-1098061598 suggests
                  //
                  // filterSecurityInterceptorOncePerRequest(false)
                  //
                  // until spring boot 3.0.  Since
                  //
                  // .antMatchers("/error").permitAll()
                  //
                  // permits unauthorized access to /error, filterSecurityInterceptorOncePerRequest
                  // isn't relevant.
                  .requestMatchers("/error")
                  .permitAll()
                  .requestMatchers("/favicon.ico")
                  .permitAll()
                  .requestMatchers(HttpMethod.OPTIONS, "/**")
                  .permitAll()
                  .requestMatchers(PermissionRevokingLogoutSuccessHandler.LOGGED_OUT_URL)
                  .permitAll()
                  .requestMatchers("/auth/user")
                  .permitAll()
                  // Public key material so downstream services can verify Gate-minted identity
                  // tokens (derived from services.gate.baseUrl). Server-to-server, unauthenticated.
                  .requestMatchers(HttpMethod.GET, "/auth/jwks")
                  .permitAll()
                  // Server-side token exchange: a downstream service swaps an opaque spk_ token
                  // (presented directly to it) for the signed identity token Gate would have
                  // minted. The spk_ token in the body is itself the credential, so the endpoint
                  // is unauthenticated and returns 401 for an unknown/expired token.
                  .requestMatchers(HttpMethod.POST, "/auth/apiTokens/exchange")
                  .permitAll()
                  .requestMatchers("/plugins/deck/**")
                  .permitAll();
              var webhooks = registry.requestMatchers(HttpMethod.POST, "/webhooks/**");
              if (webhookDefaultAuthEnabled) {
                webhooks.authenticated();
              } else {
                webhooks.permitAll();
              }
              registry
                  .requestMatchers(HttpMethod.POST, "/notifications/callbacks/**")
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/managed/notifications/callbacks/**")
                  .permitAll()
                  .requestMatchers("/health")
                  .permitAll()
                  .requestMatchers("/**")
                  .authenticated();
            })
        .logout(
            logout ->
                logout
                    .logoutUrl("/auth/logout")
                    .logoutSuccessHandler(permissionRevokingLogoutSuccessHandler)
                    .permitAll())
        .csrf(AbstractHttpConfigurer::disable);

    // Edge inbound verification: only acts when an identity token is actually present on the
    // request (service-to-service), never clobbering an established browser/CLI edge session.
    if (identityTokenAuthenticationConverter != null) {
      http.addFilterBefore(
          new GateIdentityTokenInboundFilter(identityTokenAuthenticationConverter),
          AnonymousAuthenticationFilter.class);
    }

    // After authentication is established, mint the short-lived signed identity token for the
    // caller and stash it in the MDC so it propagates to downstream services.
    if (gateIdentityService != null) {
      http.addFilterAfter(
          new IdentityTokenPropagationFilter(gateIdentityService),
          AnonymousAuthenticationFilter.class);
    }
  }
}
