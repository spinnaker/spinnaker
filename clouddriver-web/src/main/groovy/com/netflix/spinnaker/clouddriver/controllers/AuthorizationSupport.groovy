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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Support for controllers requiring authorization checks from Fiat.
 */
@Component
class AuthorizationSupport {

  @Autowired
  FiatPermissionEvaluator permissionEvaluator

  /**
   * Performs READ authorization checks on returned Maps that are keyed by account name.
   * @param map Objected returned by a controller that has account names as the key
   * @return true always, to conform to Spring Security annotation expectation.
   */
  boolean filterForAccounts(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return true
    }

    Authentication auth = SecurityContextHolder.context.authentication;

    new HashMap(map).keySet().each { String account ->
      if (!permissionEvaluator.hasPermission(auth, account, 'ACCOUNT', 'READ')) {
        map.remove(account)
      }
    }
    return true
  }
}
