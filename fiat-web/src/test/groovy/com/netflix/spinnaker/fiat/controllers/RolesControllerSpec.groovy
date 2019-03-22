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

package com.netflix.spinnaker.fiat.controllers

import com.netflix.hystrix.strategy.HystrixPlugins
import com.netflix.spinnaker.config.FiatSystemTest
import com.netflix.spinnaker.config.TestUserRoleProviderConfig.TestUserRoleProvider
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import com.netflix.spinnaker.fiat.providers.internal.IgorService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import retrofit.RetrofitError
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@FiatSystemTest
class RolesControllerSpec extends Specification {

  @Autowired
  WebApplicationContext wac

  @Autowired
  Front50Service stubFront50Service

  @Autowired
  ClouddriverService stubClouddriverService

  @Autowired
  IgorService stubIgorService

  @Autowired
  PermissionsRepository permissionsRepository

  @Autowired
  TestUserRoleProvider userRoleProvider

  @Delegate
  FiatSystemTestSupport fiatIntegrationTestSupport = new FiatSystemTestSupport()

  MockMvc mockMvc

  def setup() {
    HystrixPlugins.reset()
    this.mockMvc = MockMvcBuilders
        .webAppContextSetup(this.wac)
        .defaultRequest(get("/").content().contentType("application/json"))
        .build()
  }

  def "should put user in the repo"() {
    setup:
    stubFront50Service.getAllServiceAccounts() >> []
    stubFront50Service.getAllApplicationPermissions() >> [unrestrictedApp, restrictedApp]
    stubClouddriverService.getAccounts() >> [unrestrictedAccount, restrictedAccount]
    stubIgorService.allBuildServices >> []

    userRoleProvider.userToRoles = [
        "noRolesUser@group.com"   : [],
        "roleAUser@group.com"     : [roleA],
        "roleAroleBUser@group.com": [roleA],  // roleB comes in "externally"
    ]

    when:
    mockMvc.perform(post("/roles/noRolesUser@group.com")).andExpect(status().isOk())

    then:
    def expected = new UserPermission().setId("noRolesUser@group.com")
    permissionsRepository.get("noRolesUser@group.com").get() == expected

    when:
    mockMvc.perform(post("/roles/roleAUser@group.com")).andExpect(status().isOk())
    expected = new UserPermission().setId("roleAUser@group.com")
                                   .setRoles([roleA] as Set)
                                   .setApplications([restrictedApp] as Set)

    then:
    permissionsRepository.get("roleAUser@group.com").get() == expected

    when:
    mockMvc.perform(put("/roles/roleBUser@group.com").content('["roleB"]')).andExpect(status().isOk())
    expected = new UserPermission().setId("roleBUser@group.com")
                                   .setRoles([roleB] as Set)
                                   .setAccounts([restrictedAccount] as Set)

    then:
    permissionsRepository.get("roleBUser@group.com").get() == expected

    when:
    mockMvc.perform(put("/roles/roleAroleBUser@group.com").content('["roleB"]')).andExpect(status().isOk())
    expected = new UserPermission().setId("roleAroleBUser@group.com")
                                   .setRoles([roleA, roleB] as Set)
                                   .setApplications([restrictedApp] as Set)
                                   .setAccounts([restrictedAccount] as Set)

    then:
    permissionsRepository.get("roleAroleBUser@group.com").get() == expected

    when:
    mockMvc.perform(put("/roles/expectedError").content('["batman"]')).andExpect(status().is5xxServerError())

    then:
    stubFront50Service.getAllApplicationPermissions() >> {
      throw RetrofitError.networkError("test1", new IOException("test2"))
    }

    when:
    mockMvc.perform(delete("/roles/noRolesUser@group.com")).andExpect(status().isOk())

    then:
    !permissionsRepository.get("noRolesUser@group.com").isPresent()
  }
}
