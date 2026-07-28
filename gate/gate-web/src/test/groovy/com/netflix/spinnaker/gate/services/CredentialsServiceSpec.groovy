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
import com.netflix.spinnaker.gate.services.internal.ClouddriverService.AccountDetails
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CredentialsServiceSpec extends Specification {

  @Unroll
  def "should locally filter accounts by WRITE permissions when ignoreAuthStatus is true"() {
    setup:
    ClouddriverService clouddriverService = Stub(ClouddriverService) {
      getAccountDetails() >> Calls.response(accounts)
    }

    AccountLookupService accountLookupService = new DefaultProviderLookupService(clouddriverService)
    accountLookupService.refreshCache()

    @Subject
    CredentialsService credentialsService = new CredentialsService(accountLookupService)

    expect:
    credentialsService.getAccountNames(roles, true) == expectedAccounts

    where:
    roles              | accounts                                             || expectedAccounts
    null               | []                                                   || []
    []                 | []                                                   || []
    [null]             | []                                                   || []
    ["roleA"]          | [acnt("acntA")]                                      || ["acntA"]
    ["roleA"]          | [acnt("acntB")]                                      || ["acntB"]
    ["roleA", "roleB"] | [acnt("acntA"), acnt("acntB")]                       || ["acntA", "acntB"]
    ["roleA"]          | [acnt("acntA", [:])]                                 || ["acntA"]
    ["roleA"]          | [acnt("acntA", [WRITE: []])]                         || []
    ["roleA"]          | [acnt("acntA", [READ: ['roleA']])]                   || []
    ["roleA"]          | [acnt("acntA", [READ: ['roleA'], WRITE: ['roleA']])] || ['acntA']
    ["ROLEA"]          | [acnt("acntA", [READ: ['roleA'], WRITE: ['roleA']])] || ['acntA']
    ["roleA"]          | [acnt("acntA", [READ: ['roleA'], WRITE: ['ROLEA']])] || ['acntA']
  }

  @Unroll
  def "should return all accounts unfiltered when ignoreAuthStatus is false (downstream enforces ACLs)"() {
    setup:
    ClouddriverService clouddriverService = Stub(ClouddriverService) {
      getAccountDetails() >> Calls.response(accounts)
    }

    AccountLookupService accountLookupService = new DefaultProviderLookupService(clouddriverService)
    accountLookupService.refreshCache()

    @Subject
    CredentialsService credentialsService = new CredentialsService(accountLookupService)

    expect:
    credentialsService.getAccountNames(roles) as Set == expectedAccounts as Set

    where:
    roles     | accounts                                             || expectedAccounts
    ["roleA"] | [acnt("acntA", [READ: ['roleB'], WRITE: ['roleB']])] || ["acntA"]
    []        | [acnt("acntA")]                                      || ["acntA"]
  }

  static AccountDetails acnt(String name, Map<String, List<String>> permissions = null) {
    new AccountDetails(name: name, permissions: permissions)
  }
}
