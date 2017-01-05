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
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.authentication.configurers.GlobalAuthenticationConfigurerAdapter
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException

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
    }
  }
}
