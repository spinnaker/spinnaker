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

package com.netflix.spinnaker.gate.security.anonymous

import com.netflix.spinnaker.gate.services.AccountsService
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.security.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity

@ConditionalOnMissingBean(annotation = SpinnakerAuthConfig.class)
@Configuration
@EnableWebMvcSecurity
class AnonymousConfig extends WebSecurityConfigurerAdapter {
  String key = "spinnaker-anonymous"
  String defaultEmail = "anonymous"

  @Autowired
  AccountsService accountsService

  void configure(HttpSecurity http) {
    def principal = new User(
        email: defaultEmail,
        roles: ["anonymous"],
        allowedAccounts: accountsService.getAllowedAccounts([] /* userRoles */)
    )

    http.anonymous()
        .key(key)
        .authorities("anonymous")
        .principal(principal)
        .and()
        .csrf().disable()
  }
}
