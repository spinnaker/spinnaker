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

import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Requires auth.anonymous.enabled to be true in Fiat configs to work properly. This
 * is because anonymous users are a special permissions case, because the "user" doesn't actually
 * exist in the backing UserRolesProvider.
 */
@ConditionalOnMissingBean(annotation = SpinnakerAuthConfig.class)
@Configuration
@Slf4j
@EnableWebSecurity
@Order(Ordered.LOWEST_PRECEDENCE)
class AnonymousConfig extends WebSecurityConfigurerAdapter {
  static String key = "spinnaker-anonymous"
  static String defaultEmail = "anonymous"

  @Autowired
  CredentialsService credentialsService

  @Autowired
  FiatStatus fiatStatus

  List<String> anonymousAllowedAccounts = new CopyOnWriteArrayList<>()

  void configure(HttpSecurity http) {
    updateAnonymousAccounts()
    // Not using the ImmutableUser version in order to update allowedAccounts.
    def principal = new User(email: defaultEmail, allowedAccounts: anonymousAllowedAccounts)

    http
      .anonymous()
        .key(key)
        .principal(principal)
        .and()
      .csrf()
        .disable()
  }

  @Scheduled(fixedDelay = 60000L)
  void updateAnonymousAccounts() {
    if (fiatStatus.isEnabled()) {
      return
    }

    try {
      def newAnonAccounts = credentialsService.getAccountNames([]) ?: []

      def toAdd = newAnonAccounts - anonymousAllowedAccounts
      def toRemove = anonymousAllowedAccounts - newAnonAccounts

      anonymousAllowedAccounts.removeAll(toRemove)
      anonymousAllowedAccounts.addAll(toAdd)
    } catch (Exception e) {
      log.warn("Error while updating anonymous accounts", e)
    }
  }
}
