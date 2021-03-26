/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.filters.FiatSessionFilter
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.gate.services.ServiceAccountFilterConfigProps
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.stereotype.Component

import javax.servlet.Filter
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Slf4j
@Configuration
@EnableConfigurationProperties([ServiceConfiguration, ServiceAccountFilterConfigProps])
class AuthConfig {

  @Autowired
  PermissionRevokingLogoutSuccessHandler permissionRevokingLogoutSuccessHandler

  @Autowired
  SecurityProperties securityProperties

  @Autowired
  FiatClientConfigurationProperties configProps

  @Autowired
  FiatStatus fiatStatus

  @Autowired
  FiatPermissionEvaluator permissionEvaluator

  @Autowired
  RequestMatcherProvider requestMatcherProvider

  @Value('${security.debug:false}')
  boolean securityDebug

  @Value('${fiat.session-filter.enabled:true}')
  boolean fiatSessionFilterEnabled

  @Value('${security.webhooks.default-auth-enabled:false}')
  boolean webhookDefaultAuthEnabled

  void configure(HttpSecurity http) throws Exception {
    // @formatter:off
    http
      .requestMatcher(requestMatcherProvider.requestMatcher())
      .authorizeRequests()
        .antMatchers('/**/favicon.ico').permitAll()
        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .antMatchers(PermissionRevokingLogoutSuccessHandler.LOGGED_OUT_URL).permitAll()
        .antMatchers('/auth/user').permitAll()
        .antMatchers('/plugins/deck/**').permitAll()
        .antMatchers(HttpMethod.POST, '/webhooks/**').permitAll()
        .antMatchers(HttpMethod.POST, '/notifications/callbacks/**').permitAll()
        .antMatchers(HttpMethod.POST, '/managed/notifications/callbacks/**').permitAll()
        .antMatchers('/health').permitAll()
        .antMatchers('/**').authenticated()
    if (fiatSessionFilterEnabled) {
      Filter fiatSessionFilter = new FiatSessionFilter(
        fiatSessionFilterEnabled,
        fiatStatus,
        permissionEvaluator)

      http.addFilterBefore(fiatSessionFilter, AnonymousAuthenticationFilter.class)
    }

    if (webhookDefaultAuthEnabled) {
      http.authorizeRequests().antMatchers(HttpMethod.POST, '/webhooks/**').authenticated()
    }

    http.logout()
        .logoutUrl("/auth/logout")
        .logoutSuccessHandler(permissionRevokingLogoutSuccessHandler)
        .permitAll()
        .and()
      .csrf()
        .disable()
    // @formatter:on
  }

  void configure(WebSecurity web) throws Exception {
    web.debug(securityDebug)
  }

  @Component
  static class PermissionRevokingLogoutSuccessHandler implements LogoutSuccessHandler, InitializingBean {

    static final String LOGGED_OUT_URL = "/auth/loggedOut"

    @Autowired
    PermissionService permissionService

    SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();

    @Override
    void afterPropertiesSet() throws Exception {
      delegate.setDefaultTargetUrl(LOGGED_OUT_URL)
    }

    @Override
    void onLogoutSuccess(HttpServletRequest request,
                         HttpServletResponse response,
                         Authentication authentication) throws IOException, ServletException {
      def username = (authentication?.getPrincipal() as User)?.username
      if (username) {
        permissionService.logout(username)
      }
      delegate.onLogoutSuccess(request, response, authentication)
    }
  }
}
