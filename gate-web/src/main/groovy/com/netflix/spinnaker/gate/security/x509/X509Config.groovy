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

package com.netflix.spinnaker.gate.security.x509

import com.netflix.spinnaker.gate.security.AuthConfig
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.security.oauth2.OAuth2SsoConfig
import com.netflix.spinnaker.gate.security.saml.SamlSsoConfig
import com.netflix.spinnaker.gate.security.saml.SamlSsoConfigurer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.security.oauth2.sso.OAuth2SsoConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpRequestResponseHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@ConditionalOnExpression('${x509.enabled:false}')
@Configuration
@SpinnakerAuthConfig
@EnableWebMvcSecurity
class X509Config {

  @Value('${x509.subjectPrincipalRegex:}')
  String subjectPrincipalRegex

  @Autowired
  AuthConfig authConfig

  @Autowired
  X509AuthenticationUserDetailsService x509AuthenticationUserDetailsService

  void configure(HttpSecurity http) {
    http.x509().authenticationUserDetailsService(x509AuthenticationUserDetailsService)

    if (subjectPrincipalRegex) {
      http.x509().subjectPrincipalRegex(subjectPrincipalRegex)
    }
  }

  /**
   * A SecurityContextRepository for use with dual authentication mechanisms (X509 + SAML|OAUTH).
   *
   * Inspectes the request for the X509Certificate attribute and delegates X509 requests to a
   * NullSecurityContextRepository while delegating any other requests to an HttpSessionSecurityContextRepository.
   */
  static class X509SecurityContextRepository implements SecurityContextRepository {
    private final SecurityContextRepository certAuthRepository = new NullSecurityContextRepository()
    private final SecurityContextRepository defaultAuthRepository = new HttpSessionSecurityContextRepository()

    private SecurityContextRepository getDelegate(HttpServletRequest req) {
      if (req.getAttribute("javax.servlet.request.X509Certificate")) {
        return certAuthRepository
      }

      return defaultAuthRepository
    }

    @Override
    SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
      return getDelegate(requestResponseHolder.request).loadContext(requestResponseHolder)
    }

    @Override
    void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
      getDelegate(request).saveContext(context, request, response)
    }

    @Override
    boolean containsContext(HttpServletRequest request) {
      return getDelegate(request).containsContext(request)
    }
  }

  /**
   * See {@link OAuth2SsoConfig} for why these classes and conditionals exist!
   */
  @ConditionalOnMissingBean([OAuth2SsoConfig, SamlSsoConfig])
  @Bean
  X509StandaloneAuthConfig standaloneConfig() {
    new X509StandaloneAuthConfig()
  }

  class X509StandaloneAuthConfig extends WebSecurityConfigurerAdapter {
    void configure(HttpSecurity http) {
      authConfig.configure(http)
      http.securityContext().securityContextRepository(new NullSecurityContextRepository())
      X509Config.this.configure(http)
    }
  }

  @ConditionalOnBean(X509StandaloneAuthConfig)
  @Bean
  AuthenticationManager authenticationManagerBean() {
    new AuthenticationManager() {
      @Override
      Authentication authenticate(Authentication authentication) throws AuthenticationException {
        throw new UnsupportedOperationException("No authentication should be done with this AuthenticationManager");
      }
    }
  }

  @ConditionalOnBean(OAuth2SsoConfig)
  @Bean
  X509OAuthConfig withOauthConfig() {
    new X509OAuthConfig()
  }

  class X509OAuthConfig implements OAuth2SsoConfigurer {
    @Override
    void match(OAuth2SsoConfigurer.RequestMatchers matchers) {
      matchers.antMatchers('/**')
    }

    @Override
    void configure(HttpSecurity http) throws Exception {
      X509Config.this.configure(http)
      http.securityContext().securityContextRepository(new X509SecurityContextRepository())
    }
  }

  @ConditionalOnBean(SamlSsoConfig)
  @Bean
  X509SamlConfig withSamlConfig() {
    new X509SamlConfig()
  }

  class X509SamlConfig implements SamlSsoConfigurer {
    @Override
    void configure(HttpSecurity http) throws Exception {
      X509Config.this.configure(http)
      http.securityContext().securityContextRepository(new X509SecurityContextRepository())
    }
  }
}
