/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2

import com.netflix.spinnaker.gate.config.AuthConfig
import com.netflix.spinnaker.gate.security.MultiAuthConfigurer
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.security.SuppportsMultiAuth
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2SsoProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter
import org.springframework.stereotype.Component

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
@Import(SecurityAutoConfiguration)
@EnableOAuth2Sso
@EnableConfigurationProperties
@SuppportsMultiAuth
@Order(Ordered.LOWEST_PRECEDENCE)
// Note the 4 single-quotes below - this is a raw groovy string, because SpEL and groovy
// string syntax overlap!
@ConditionalOnExpression(''''${security.oauth2.client.clientId:}'!=""''')
class OAuth2SsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  AuthConfig authConfig

  @Autowired
  ExternalAuthTokenFilter externalAuthTokenFilter

  @Autowired
  ExternalSslAwareEntryPoint entryPoint

  @Autowired(required = false)
  List<MultiAuthConfigurer> additionalAuthProviders

  @Primary
  @Bean
  ResourceServerTokenServices spinnakerUserInfoTokenServices() {
    new SpinnakerUserInfoTokenServices()
  }

  @Bean
  ExternalAuthTokenFilter externalAuthTokenFilter() {
    new ExternalAuthTokenFilter()
  }

  @Bean
  OAuth2SsoProperties oAuth2SsoProperties() {
    new OAuth2SsoProperties()
  }

  @Override
  void configure(HttpSecurity http) throws Exception {
    authConfig.configure(http)

    http.exceptionHandling().authenticationEntryPoint(entryPoint)
    http.addFilterBefore(externalAuthTokenFilter, AbstractPreAuthenticatedProcessingFilter.class)

    additionalAuthProviders?.each {
      it.configure(http)
    }
  }

  void configure(WebSecurity web) throws Exception {
    authConfig.configure(web)
  }

  /**
   * Use this class to specify how to map fields from the userInfoUri response to what's expected to be in the User.
   */
  @Component
  @ConfigurationProperties("security.oauth2.userInfoMapping")
  static class UserInfoMapping {
    String email = "email"
    String firstName = "given_name"
    String lastName = "family_name"
    String username = "email"
    String serviceAccountEmail = "client_email"
    String roles = "roles"
  }

  @Component
  @ConfigurationProperties("security.oauth2.userInfoRequirements")
  static class UserInfoRequirements extends HashMap<String, String> {
  }

  /**
   * This class exists to change the login redirect (to /login) to the same URL as the
   * preEstablishedRedirectUri, if set, where the SSL is terminated outside of this server.
   */
  @Component
  @ConditionalOnExpression(''''${security.oauth2.client.clientId:}'!=""''')
  static class ExternalSslAwareEntryPoint extends LoginUrlAuthenticationEntryPoint {

    @Autowired
    private AuthorizationCodeResourceDetails details

    @Autowired
    ExternalSslAwareEntryPoint(OAuth2SsoProperties sso) {
      super(sso.loginPath)
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
      return details.preEstablishedRedirectUri ?: super.determineUrlToUseForThisRequest(request, response, exception)
    }
  }
}
