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

package com.netflix.spinnaker.clouddriver.security

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
@Component
class DefaultAllowedAccountsValidator implements AllowedAccountsValidator {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  private final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  DefaultAllowedAccountsValidator(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  @Override
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors) {
    if (!accountCredentialsProvider.all.find { it.requiredGroupMembership }) {
      // no accounts have group restrictions so no need to validate / log
      return
    }

    def json = null
    try {
      json = OBJECT_MAPPER.writeValueAsString(description)
    } catch (Exception ignored) {
    }

    /*
     * Access should be allowed iff
     * - the account is not restricted (has no requiredGroupMembership)
     * - the user has been granted specific access (has the target account in its set of allowed accounts)
     */
    def requiredGroups = description.credentials.requiredGroupMembership*.toLowerCase()
    def targetAccount = description.credentials.name
    def isAuthorized = !requiredGroups || allowedAccounts.find { it.equalsIgnoreCase(targetAccount) }
    def message = "${user} is ${isAuthorized ? '' : 'not '}authorized (account: ${targetAccount}, description: ${description.class.simpleName}, allowedAccounts: ${allowedAccounts}, requiredGroups: ${requiredGroups}, json: ${json})"
    if (!isAuthorized) {
      log.warn(message)
      errors.rejectValue("credentials", "unauthorized", "${user} is not authorized (account: ${description.credentials.name}, description: ${description.class.simpleName})")
    } else {
      log.info(message)
    }
  }
}
