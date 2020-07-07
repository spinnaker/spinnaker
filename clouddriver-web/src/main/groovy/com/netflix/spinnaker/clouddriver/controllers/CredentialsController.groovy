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
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
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
  List<Map> list(@RequestParam(value = "expand", required = false) boolean expand) {
    accountCredentialsProvider.all.collect { render(expand, it) }
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  Map getAccount(@PathVariable("name") String name) {
    def accountDetail = render(true, accountCredentialsProvider.getCredentials(name))
    if (!accountDetail) {
      throw new NotFoundException("Account does not exist (name: ${name})")
    }

    return accountDetail
  }

  Map render(boolean includeDetail, AccountCredentials accountCredentials) {
    if (accountCredentials == null) {
      return null
    }
    Map cred = objectMapper.convertValue(accountCredentials, Map)
    if (!includeDetail) {
      cred.keySet().retainAll(['name',
                               'environment',
                               'accountType',
                               'cloudProvider',
                               'requiredGroupMembership',
                               'permissions',
                               'accountId'])
    }

    cred.type = accountCredentials.cloudProvider
    cred.challengeDestructiveActions = credentialsConfiguration.challengeDestructiveActionsEnvironments.contains(accountCredentials.environment)
    cred.primaryAccount = credentialsConfiguration.primaryAccountTypes.contains(accountCredentials.accountType)

    return cred
  }
}
