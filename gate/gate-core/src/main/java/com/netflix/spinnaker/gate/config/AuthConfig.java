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

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.filters.FiatSessionFilter;
import com.netflix.spinnaker.gate.filters.FleetDirectAccessFilter;
import com.netflix.spinnaker.gate.services.ServiceAccountFilterConfigProps;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({
  ServiceConfiguration.class,
  ServiceAccountFilterConfigProps.class,
  FiatClientConfigurationProperties.class,
  DynamicRoutingConfigProperties.class,
  FleetConfigurationProperties.class
})
@NonnullByDefault
@RequiredArgsConstructor
public class AuthConfig {
  private final PermissionRevokingLogoutSuccessHandler permissionRevokingLogoutSuccessHandler;
  private final FiatStatus fiatStatus;
  private final FiatPermissionEvaluator permissionEvaluator;
  private final RequestMatcherProvider requestMatcherProvider;
  private final FleetConfigurationProperties fleetConfigurationProperties;

  @Setter(
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${security.debug:false}")})
  private boolean securityDebug;

  @Setter(
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${fiat.session-filter.enabled:true}")})
  private boolean fiatSessionFilterEnabled;

  @Setter(
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${security.webhooks.default-auth-enabled:false}")})
  private boolean webhookDefaultAuthEnabled;

  /**
   * The SAML Assertion Consumer Service path, always exempted from the fleet direct-access
   * guardrail. With a per-instance ACS the IdP POSTs the assertion straight to an instance's own
   * hostname, so gating it would make login impossible for non-admins.
   */
  @Setter(
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${saml.login-processing-url:/saml/SSO}")})
  private String samlLoginProcessingUrl = "/saml/SSO";

  @Bean
  public WebSecurityCustomizer securityDebugCustomizer() {
    return web -> web.debug(securityDebug);
  }

  public void configure(HttpSecurity http) throws Exception {
    http.securityMatcher(requestMatcherProvider.requestMatcher())
        .authorizeHttpRequests(
            authz -> {
              authz
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
                  .requestMatchers("/plugins/deck/**")
                  .permitAll();
              var webhooks = authz.requestMatchers(HttpMethod.POST, "/webhooks/**");
              if (webhookDefaultAuthEnabled) {
                webhooks.authenticated();
              } else {
                webhooks.permitAll();
              }
              authz
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

    if (fiatSessionFilterEnabled) {
      var filter = new FiatSessionFilter(fiatStatus, permissionEvaluator);
      http.addFilterBefore(filter, AnonymousAuthenticationFilter.class);
    }

    if (fleetConfigurationProperties.isEnabled()) {
      // After AuthorizationFilter so the SecurityContext is populated and admin status is known.
      var filter =
          new FleetDirectAccessFilter(
              fleetConfigurationProperties, permissionEvaluator, samlLoginProcessingUrl);
      http.addFilterAfter(filter, AuthorizationFilter.class);
    }
  }
}
