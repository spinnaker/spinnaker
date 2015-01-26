/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.gate.security.onelogin.AccountSettings
import com.netflix.spinnaker.gate.security.onelogin.AppSettings
import com.netflix.spinnaker.gate.security.onelogin.saml.AuthRequest
import com.netflix.spinnaker.gate.security.onelogin.saml.Response
import groovy.transform.Immutable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@ConditionalOnProperty('onelogin.enabled')
@EnableWebSecurity
@Configuration
@EnableConfigurationProperties
@Import(SecurityAutoConfiguration)
class OneLoginSecurityConfig extends WebSecurityConfigurerAdapter {

  @Component
  @ConfigurationProperties("onelogin")
  static class OneLoginSecurityConfigProperties {
    Boolean enabled
    String url
    String certificate
    String redirectBase
    String requiredRole
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.csrf().disable().rememberMe().rememberMeServices(rememberMeServices())
  }

  @Bean
  public RememberMeServices rememberMeServices() {
    TokenBasedRememberMeServices rememberMeServices = new TokenBasedRememberMeServices("password", userDetailsService())
    rememberMeServices.setCookieName("cookieName")
    rememberMeServices.setParameter("rememberMe")
    rememberMeServices
  }

  @ConditionalOnExpression('${onelogin.enabled:false}')
  @RequestMapping("/auth")
  @RestController
  static class OneLoginSecurityController {
    private static final String SPINNAKER_SSO_CALLBACK_KEY = "_SPINNAKER_SSO_CALLBACK"

    private final String url
    private final String certificate
    private final OneLoginSecurityConfigProperties oneLoginProperties

    @Autowired
    OneLoginSecurityController(OneLoginSecurityConfigProperties properties) {
      this.url = properties.url
      this.certificate = properties.certificate
      this.oneLoginProperties = properties
    }

    @Autowired
    RememberMeServices rememberMeServices

    @RequestMapping(method = RequestMethod.GET)
    void get(
        @RequestParam(value = "callback", required = false) String cb,
        @RequestParam(value = "path", required = false) String hash,
        HttpServletRequest request, HttpServletResponse response) {
      URL redirect
      if (oneLoginProperties.redirectBase) {
        redirect = (oneLoginProperties.redirectBase + '/auth/signIn').toURI().normalize().toURL()
      } else {
        redirect = new URL(request.scheme, request.serverName, request.serverPort, request.contextPath + '/auth/signIn')
      }
      def appSettings = new AppSettings(issuer: url, assertionConsumerServiceUrl: redirect)
      def authReq = new AuthRequest(appSettings)
      def samlReq = URLEncoder.encode(authReq.request, 'UTF-8')

      def callback = cb && hash ? cb + '/#' + hash : cb

      request.session.setAttribute(SPINNAKER_SSO_CALLBACK_KEY, callback)

      response.status = 302
      response.addHeader("Location", "${url}?SAMLRequest=${samlReq}")
    }

    @RequestMapping(value = "/signIn", method = RequestMethod.POST)
    void signIn(
        @RequestParam("SAMLResponse") String samlResponse, HttpServletRequest request, HttpServletResponse response) {
      def accountSettings = new AccountSettings(certificate: certificate)
      def resp = new Response(accountSettings)
      resp.loadXmlFromBase64(samlResponse)

      def user = new User(resp.nameId, resp.getAttribute("User.FirstName"), resp.getAttribute("User.LastName"),
          resp.getAttribute("memberOf"))
      if (!hasRequiredRole(user)) {
        throw new BadCredentialsException("Credentials are bad")
      }
      def auth = new UsernamePasswordAuthenticationToken(user, "", [new SimpleGrantedAuthority("USER")])
      SecurityContextHolder.context.authentication = auth
      rememberMeServices.loginSuccess(request, response, auth)

      String callback = request.session.getAttribute(SPINNAKER_SSO_CALLBACK_KEY)
      if (!callback) {
        response.sendError(200, "ok")
        return;
      }

      response.sendRedirect callback
    }

    boolean hasRequiredRole(User user) {
      if (oneLoginProperties.requiredRole) {
        return user.memberOf.contains(oneLoginProperties.requiredRole)
      }
      return true
    }

    @RequestMapping(value = "/info", method = RequestMethod.GET)
    User getUser(HttpServletResponse response) {
      Object whoami = SecurityContextHolder.context.authentication.principal
      if (!whoami || !(whoami instanceof User) || !(hasRequiredRole(whoami))) {
        response.addHeader GateConfig.AUTHENTICATION_REDIRECT_HEADER_NAME, "/auth"
        response.sendError 401
        null
      } else {
        (User) whoami
      }
    }

    @Immutable
    static class User {
      String email
      String firstName
      String lastName
      String memberOf
    }
  }
}
