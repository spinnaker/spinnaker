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

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class CredentialsService {
  private static final String GROUP = "credentials"

  @Autowired
  KatoService katoService

  List<KatoService.Account> getAccounts() {
    HystrixFactory.newListCommand(GROUP, "getAccounts") {
      def allAccounts = katoService.accounts

      if (!AuthenticatedRequest.getSpinnakerUser().present) {
        // if the request is unauthenticated, return only anonymously accessible accounts (no group membership required)
        return allAccounts.findAll { !it.requiredGroupMembership }
      }

      def allowedAccountsOptional = AuthenticatedRequest.getSpinnakerAccounts()
      def allowedAccounts = allowedAccountsOptional.orElse("").split(",").collect { it.toLowerCase() }
      return allAccounts.findAll {
        allowedAccounts.contains(it.name.toLowerCase())
      }
    } execute()
  }

  Map getAccount(String account) {
    HystrixFactory.newMapCommand(GROUP, "getAccount") {
      katoService.getAccount(account)
    } execute()
  }
}
