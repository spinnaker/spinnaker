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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class CredentialsServiceSpec extends Specification {
  @Shared
  List<ClouddriverService.Account> accounts = [
    new ClouddriverService.Account(name: "account1", type: "aws"),
    new ClouddriverService.Account(name: "account2", type: "aws")
  ]

  ClouddriverService clouddriverService = Mock(ClouddriverService) {
    1 * getAccounts() >> { accounts }
    0 * _
  }

  void "should return all accounts if no authenticated user"() {
    expect:
    new CredentialsService(clouddriverService: clouddriverService).getAccounts() == accounts
  }

  @Unroll
  void "should filter accounts based on authenticated user"() {
    expect:
    AuthenticatedRequest.propagate({
      new CredentialsService(clouddriverService: clouddriverService).getAccounts()
    }, false, new User(email: "email", roles: [], allowedAccounts: userAccounts, username: "email")).call() as List<ClouddriverService.Account> == allowedAccounts

    where:
    userAccounts                         || allowedAccounts
    ["account1"]                         || [accounts[0]]
    ["account2"]                         || [accounts[1]]
    ["account1", "account2"]             || accounts
    ["account1", "account2", "account3"] || accounts
    []                                   || accounts
    null                                 || accounts
  }
}
