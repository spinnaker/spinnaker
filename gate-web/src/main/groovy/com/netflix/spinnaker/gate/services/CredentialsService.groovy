/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.gate.services.internal.ClouddriverService.AccountDetails
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Slf4j
@Service
class CredentialsService {
  @Autowired
  AccountLookupService accountLookupService

  @Autowired
  FiatClientConfigurationProperties fiatConfig

  Collection<String> getAccountNames() {
    getAccountNames([])
  }

  Collection<String> getAccountNames(Collection<String> userRoles) {
    getAccounts(userRoles)*.name
  }

  /**
   * Returns all account names that a user with the specified list of userRoles has access to.
   */
  List<AccountDetails> getAccounts(Collection<String> userRoles) {
    final Set<String> userRolesLower = userRoles*.toLowerCase() as Set<String>
    return accountLookupService.getAccounts().findAll { AccountDetails account ->
      if (fiatConfig.enabled) {
        return true // Returned list is filtered later.
      }

      if (!account.permissions) {
        return true
      }

      Set<String> permissions = account.permissions.WRITE*.toLowerCase() ?: []

      return userRolesLower.intersect(permissions) as Boolean
    } ?: []
  }
}
