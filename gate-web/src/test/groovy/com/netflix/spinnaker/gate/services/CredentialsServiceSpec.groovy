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

import com.netflix.spinnaker.config.FiatClientConfigurationProperties
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CredentialsServiceSpec extends Specification {

  @Unroll
  def "should return allowed account names"() {
    setup:
      AccountLookupService accountLookupService = Mock(AccountLookupService) {
        getAccounts() >> accounts
      }
      FiatClientConfigurationProperties fiatConfig = new FiatClientConfigurationProperties(enabled: false)

      @Subject
      CredentialsService credentialsService = new CredentialsService(accountLookupService: accountLookupService,
                                                                     fiatConfig: fiatConfig)

    when:
      def allowedAccounts = credentialsService.getAccountNames(roles)

    then:
      allowedAccounts == expectedAccounts

    where:
      roles              | accounts                       || expectedAccounts
      null               | []                             || []
      []                 | []                             || []
      [null]             | []                             || []
      ["roleA"]          | [acnt("acntA")]                || ["acntA"]
      ["roleA"]          | [acnt("acntB")]                || ["acntB"]
      ["roleA", "roleB"] | [acnt("acntA"), acnt("acntB")] || ["acntA", "acntB"]
      ["roleA"]          | [acnt("acntA", "roleA")]       || ["acntA"]
      ["ROLEA"]          | [acnt("acntA", "rolea")]       || ["acntA"]
      ["roleA"]          | [acnt("acntA", "roleB")]       || []
  }

  static ClouddriverService.Account acnt(String name, String... reqGroupMembership) {
    new ClouddriverService.Account(name: name, requiredGroupMembership: reqGroupMembership)
  }
}
