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

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.services.AccountLookupService
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.accept.ContentNegotiationManagerFactoryBean
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class CredentialsControllerSpec extends Specification {

  MockMvc mockMvc
  ClouddriverService clouddriverService = Mock(ClouddriverService)
  AccountLookupService accountLookupService = Stub(AccountLookupService) {
    getAccounts() >> [new ClouddriverService.AccountDetails(name: "test"),
                      new ClouddriverService.AccountDetails(name: "test.com")]
  }

  void setup() {
    def fiatStatus = Mock(FiatStatus) {
      _ * isEnabled() >> { return false }
    }

    @Subject
    CredentialsService credentialsService = new CredentialsService(
      accountLookupService: accountLookupService,
      fiatStatus: fiatStatus
    )

    FiatPermissionEvaluator fpe = Stub(FiatPermissionEvaluator)
    AllowedAccountsSupport allowedAccountsSupport = new AllowedAccountsSupport(fiatStatus, fpe, credentialsService)

    def contentNegotiationManagerFactoryBean = new ContentNegotiationManagerFactoryBean()
    contentNegotiationManagerFactoryBean.addMediaType("json", MediaType.APPLICATION_JSON)
    contentNegotiationManagerFactoryBean.favorPathExtension = false
    mockMvc = MockMvcBuilders
      .standaloneSetup(new CredentialsController(accountLookupService:  accountLookupService, allowedAccountsSupport: allowedAccountsSupport))
      .setContentNegotiationManager(contentNegotiationManagerFactoryBean.build())
      .build()
  }

  @Unroll
  def "should accept account names with dots"() {
    when:
    MockHttpServletResponse response = mockMvc.perform(get("/credentials/${account}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.status == 200
    response.contentAsString == "{\"name\":\"${expectedAccount}\",\"requiredGroupMembership\":[],\"authorized\":true}"

    where:
    account    || expectedAccount
    "test"     || "test"
    "test.com" || "test.com"
  }
}
