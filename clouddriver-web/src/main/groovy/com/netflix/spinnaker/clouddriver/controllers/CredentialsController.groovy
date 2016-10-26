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

package com.netflix.spinnaker.clouddriver.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/credentials")
class CredentialsController {

  @Autowired
  CredentialsConfiguration credentialsConfiguration

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  MessageSource messageSource

  @RequestMapping(method = RequestMethod.GET)
  List<Map> list() {
    accountCredentialsProvider.all.collect(this.&renderSummary)
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  Map getAccount(@PathVariable("name") String name) {
    def accountDetail = renderDetail(accountCredentialsProvider.getCredentials(name))
    if (!accountDetail) {
      throw new AccountNotFoundException(account: name)
    }

    return accountDetail
  }

  Map renderSummary(AccountCredentials accountCredentials) {
    render(false, accountCredentials)
  }

  Map renderDetail(AccountCredentials accountCredentials) {
    render(true, accountCredentials)
  }

  Map render(boolean includeDetail, AccountCredentials accountCredentials) {
    if (accountCredentials == null) {
      return null
    }
    Map cred = objectMapper.convertValue(accountCredentials, Map)
    if (!includeDetail) {
      cred.keySet().retainAll(['name', 'environment', 'accountType', 'cloudProvider', 'requiredGroupMembership', 'accountId'])
    }

    cred.type = accountCredentials.cloudProvider
    cred.challengeDestructiveActions = credentialsConfiguration.challengeDestructiveActionsEnvironments.contains(accountCredentials.environment)
    cred.primaryAccount = credentialsConfiguration.primaryAccountTypes.contains(accountCredentials.accountType)

    return cred
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleAccountNotFound(AccountNotFoundException ex) {
    def message = messageSource.getMessage("account.not.found", [ex.account] as String[], "account.not.found", LocaleContextHolder.locale)
    [error: "account.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  @ResponseStatus(value=HttpStatus.NOT_FOUND)
  static class AccountNotFoundException extends RuntimeException {
    String account
  }

}
