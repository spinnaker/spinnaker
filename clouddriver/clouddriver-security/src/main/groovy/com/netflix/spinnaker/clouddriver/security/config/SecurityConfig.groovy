/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.security.config

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AllowedAccountsValidator
import com.netflix.spinnaker.clouddriver.security.DefaultAllowedAccountsValidator

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Authorization is owner-local and wired by {@code ClouddriverSecurityConfig} (verifier-only
 * identity-token chain + {@code spinnakerPermissionEvaluator} bean). This config only contributes
 * Clouddriver's operation-security properties and the local allowed-accounts validator.
 */
@Configuration
@EnableConfigurationProperties(OperationsSecurityConfigurationProperties)
class SecurityConfig {
  @Bean
  AllowedAccountsValidator allowedAccountsValidator(AccountCredentialsProvider accountCredentialsProvider) {
    return new DefaultAllowedAccountsValidator(accountCredentialsProvider)
  }

  @ConfigurationProperties("operations.security")
  static class OperationsSecurityConfigurationProperties {
    SecurityAction onMissingSecuredCheck = SecurityAction.WARN
    SecurityAction onMissingValidator = SecurityAction.WARN

    //TODO(jonsie): should be `allowUnauthorizedImageTaggingInAccounts`
    List<String> allowUnauthenticatedImageTaggingInAccounts = []
  }

  static enum SecurityAction {
    IGNORE,
    WARN,
    FAIL
  }
}
