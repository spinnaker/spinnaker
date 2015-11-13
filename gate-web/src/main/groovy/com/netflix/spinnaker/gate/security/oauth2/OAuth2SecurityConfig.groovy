/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2

import com.netflix.spinnaker.gate.security.WebSecurityAugmentor
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationProcessingFilter
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

@Slf4j
@CompileStatic
@Configuration
@ConditionalOnExpression('${oauth2.enabled:false}')
class OAuth2SecurityConfig implements WebSecurityAugmentor {
  @Override
  void configure(HttpSecurity http, UserDetailsService userDetailsService, AuthenticationManager authenticationManager) {
    def filter = new OAuth2AuthenticationProcessingFilter() {
      @Override
      void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (AuthenticatedRequest.getSpinnakerUser().isPresent()) {
          // No need to attempt OAuth if user is already authenticated
          chain.doFilter(req, res)
          return
        }
        super.doFilter(req, res, chain)
      }
    }

    filter.setAuthenticationManager(authenticationManager)
    http.addFilterBefore(filter, BasicAuthenticationFilter)

    http.csrf().disable()
  }

  @Override
  void configure(AuthenticationManagerBuilder authenticationManagerBuilder) {
    authenticationManagerBuilder.authenticationProvider(
      authenticationProvider(identityResourceServerTokenServices(restTemplate()))
    )
  }

  @Bean
  AuthenticationProvider authenticationProvider(IdentityResourceServerTokenServices identityResourceServerTokenServices) {
    return new OAuth2AuthenticationProvider(identityResourceServerTokenServices)
  }

  @Bean
  IdentityResourceServerTokenServices identityResourceServerTokenServices(RestOperations restOperations) {
    def defaultAccessTokenConverter = new DefaultAccessTokenConverter()
    defaultAccessTokenConverter.userTokenConverter = new DefaultUserAuthenticationConverter()

    return new IdentityResourceServerTokenServices(
      identityServerConfiguration(), restOperations, defaultAccessTokenConverter
    )
  }

  @Bean
  @ConfigurationProperties('oauth2')
  IdentityResourceServerTokenServices.IdentityServerConfiguration identityServerConfiguration() {
    new IdentityResourceServerTokenServices.IdentityServerConfiguration()
  }

  @Bean
  @ConditionalOnMissingBean(RestOperations)
  RestOperations restTemplate() {
    new RestTemplate()
  }

  static class DefaultUserAuthenticationConverter implements UserAuthenticationConverter {
    @Override
    Map<String, ?> convertUserAuthentication(Authentication userAuthentication) {
      return [:]
    }

    @Override
    Authentication extractAuthentication(Map<String, ?> map) {
      def allowedAccounts = (map.scope ?: []).collect { String scope -> scope.replace("spinnaker_", "")}
      def user = new User(map.client_id as String, null, null, [], allowedAccounts)
      return new UsernamePasswordAuthenticationToken(user, "N/A", [])
    }
  }
}
