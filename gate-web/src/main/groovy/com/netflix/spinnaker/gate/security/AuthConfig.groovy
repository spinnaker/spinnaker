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

package com.netflix.spinnaker.gate.security

import com.netflix.spinnaker.gate.security.rolesprovider.UserRolesProvider
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity

@Slf4j
@Configuration
class AuthConfig {

  @Bean
  @ConditionalOnMissingBean(UserRolesProvider)
  UserRolesProvider defaultUserRolesProvider() {
    return new UserRolesProvider() {
      @Override
      Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
        return [:]
      }

      @Override
      Collection<String> loadRoles(String userEmail) {
        return []
      }
    }
  }

  static void configure(HttpSecurity http) throws Exception {
    http
      .authorizeRequests()
        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .antMatchers('/auth/user').permitAll()
        .antMatchers('/health').permitAll()
        .antMatchers('/**').authenticated()
        .and()
      .logout()
        .logoutUrl("/auth/logout")
        .logoutSuccessUrl("/auth/loggedOut")
        .permitAll()
        .and()
      .csrf()
        .disable()
  }
}
