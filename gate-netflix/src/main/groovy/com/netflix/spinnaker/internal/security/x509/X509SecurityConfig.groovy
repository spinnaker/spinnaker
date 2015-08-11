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

package com.netflix.spinnaker.internal.security.x509

import com.netflix.spinnaker.internal.security.AnonymousAccountsService
import com.netflix.spinnaker.internal.security.WebSecurityAugmentor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter

@ConditionalOnExpression('${x509.enabled:false}')
@Configuration
class X509SecurityConfig implements WebSecurityAugmentor {

  @Autowired
  AnonymousAccountsService anonymousAccountsService

  @Override
  void configure(HttpSecurity http,
                 UserDetailsService userDetailsService,
                 AuthenticationManager authenticationManager) {
    def filter = new X509AuthenticationFilter()
    filter.setAuthenticationManager(authenticationManager)
    http.addFilter(filter)

    http.csrf().disable()
  }

  @Override
  void configure(AuthenticationManagerBuilder auth) {
    auth.authenticationProvider(new X509AuthenticationProvider(anonymousAccountsService))
  }
}
