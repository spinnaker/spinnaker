/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.gate.services.AccountLookupService
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class CredentialsControllerTest extends Specification {

  MockMvc mockMvc
  ClouddriverService clouddriverService
  ClouddriverServiceSelector clouddriverServiceSelector

  def server = new MockWebServer()

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    AccountLookupService  accountLookupService = Mock(AccountLookupService) {
      getAccounts() >> ["test", "test.com"]
    }
    FiatClientConfigurationProperties fiatConfig = new FiatClientConfigurationProperties(enabled: false)

    clouddriverService = Mock(ClouddriverService)
    clouddriverServiceSelector = Mock(ClouddriverServiceSelector)

    @Subject
    CredentialsService credentialsService = new CredentialsService(accountLookupService: accountLookupService,
      clouddriverServiceSelector: clouddriverServiceSelector,
      fiatConfig: fiatConfig)

    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new CredentialsController(credentialsService: credentialsService)).build()
  }

  @Unroll
  def "should accept account names with dots"() {
    given:
    1 * clouddriverServiceSelector.select(_) >> clouddriverService
    1 * clouddriverService.getAccount(account) >> ["accountName": account]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/credentials/${account}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == "{\"accountName\":\"${expectedAccount}\"}"

    where:
    account    || expectedAccount
    "test"     || "test"
    "test.com" || "test.com"
  }
}
