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

import com.netflix.spinnaker.gate.security.AuthConfig
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.security.rolesprovider.UserRolesProvider
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.security.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.security.oauth2.resource.ResourceServerProperties
import org.springframework.cloud.security.oauth2.resource.UserInfoTokenServices
import org.springframework.cloud.security.oauth2.sso.EnableOAuth2Sso
import org.springframework.cloud.security.oauth2.sso.OAuth2SsoConfigurer
import org.springframework.cloud.security.oauth2.sso.OAuth2SsoConfigurerAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.OAuth2Request
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Each time a class extends WebSecurityConfigurerAdapter, a new set of matchers + filters gets added
 * to the FilterChainProxy. This is not great when we want to protect the same URLs with different kinds of
 * security mechanisms, because only the first set of filters that hit the matchers will be executed.
 *
 * In order to get around this, we must only have 1 class that extends WebSecurityConfigurerAdapter.
 * Unfortunately, the OAuth2 library (sping-security-oauth) has such a class baked in. If we have OAuth2 enabled,
 * AND want an additional kind of security mechanism (X509, for example), we must use the
 * {@link OAuth2SsoConfigurerAdapter} hook the library has provided. This way, only a single set of matchers
 * and filters are added.
 */
@Configuration
@SpinnakerAuthConfig
// Use @EnableWebSecurity if/when updated to Spring Security 4.
@EnableWebMvcSecurity
@Import(SecurityAutoConfiguration)
@EnableOAuth2Sso
// Note the 4 single-quotes below - this is a raw groovy string, because SpEL and groovy
// string syntax overlap!
@ConditionalOnExpression(''''${spring.oauth2.client.clientId:}'!=""''')
class OAuth2SsoConfig extends OAuth2SsoConfigurerAdapter {

  @Override
  void match(OAuth2SsoConfigurer.RequestMatchers matchers) {
    matchers.antMatchers('/**')
  }

  @Override
  void configure(HttpSecurity http) throws Exception {
    AuthConfig.configure(http)
  }

  /**
   * ResourceServerTokenServices is an interface used to manage access tokens. The UserInfoTokenService object is an
   * implementation of that interface that uses an access token to get the logged in user's data (such as email or
   * profile). We want to customize the Authentication object that is returned to include our custom (Kork) User.
   */
  @Primary
  @Bean
  ResourceServerTokenServices spinnakerAuthorityInjectedUserInfoTokenServices() {
    return new ResourceServerTokenServices() {

      @Autowired
      ResourceServerProperties sso

      @Autowired
      UserInfoTokenServices userInfoTokenServices

      @Autowired
      CredentialsService credentialsService

      @Autowired
      UserRolesProvider userRolesProvider

      @Autowired
      UserInfoMapping userInfoMapping

      @Override
      OAuth2Authentication loadAuthentication(String accessToken) throws AuthenticationException, InvalidTokenException {
        OAuth2Authentication oAuth2Authentication = userInfoTokenServices.loadAuthentication(accessToken)

        Map details = oAuth2Authentication.userAuthentication.details as Map
        def username = details[userInfoMapping.username] as String
        def roles = userRolesProvider.loadRoles(username)

        User spinnakerUser = new User(
            email: details[userInfoMapping.email] as String,
            firstName: details[userInfoMapping.firstName] as String,
            lastName: details[userInfoMapping.lastName] as String,
            allowedAccounts: credentialsService.getAccountNames(roles),
            roles: roles,
            username: username).asImmutable()

        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
            spinnakerUser,
            null /* credentials */,
            spinnakerUser.authorities
        )

        // impl copied from userInfoTokenServices
        OAuth2Request storedRequest = new OAuth2Request(null, sso.clientId, null, true /*approved*/,
                                                        null, null, null, null, null);

        return new OAuth2Authentication(storedRequest, authentication)
      }

      @Override
      OAuth2AccessToken readAccessToken(String accessToken) {
        return userInfoTokenServices.readAccessToken(accessToken)
      }
    }
  }

  /**
   * Use this class to specify how to map fields from the userInfoUri response to what's expected to be in the User.
   */
  @Component
  @ConfigurationProperties("spring.oauth2.userInfoMapping")
  static class UserInfoMapping {
    String email = "email"
    String firstName = "given_name"
    String lastName = "family_name"
    String username = "email"
  }
}
