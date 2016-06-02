/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AccountsService {

  @Autowired
  ClouddriverService clouddriverService

  /**
   * Returns all account names that a user with the specified list of userRoles has access to.
   */
  public Collection<String> getAllowedAccounts(Collection<String> userRoles) {
    return clouddriverService.accounts.findAll { ClouddriverService.Account account ->
      if (!account.requiredGroupMembership) {
        return true // anonymous account.
      }

      def userRolesLower = userRoles*.toLowerCase()
      def reqGroupMembershipLower = account.requiredGroupMembership*.toLowerCase()

      return userRolesLower.intersect(reqGroupMembershipLower) as Boolean
    }*.name ?: []
  }
}
