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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.hystrix.strategy.HystrixPlugins
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.FiatSystemTest
import com.netflix.spinnaker.config.TestUserRoleProviderConfig
import com.netflix.spinnaker.fiat.config.FiatServerConfigurationProperties
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@FiatSystemTest
class AuthorizeControllerSpec extends Specification {
  @Shared
  Registry registry = new NoopRegistry()

  @Autowired
  WebApplicationContext wac

  @Autowired
  Front50Service stubFront50Service

  @Autowired
  ClouddriverService stubClouddriverService

  @Autowired
  PermissionsRepository permissionsRepository

  @Autowired
  FiatServerConfigurationProperties fiatServerConfigurationProperties

  @Autowired
  TestUserRoleProviderConfig.TestUserRoleProvider userRoleProvider

  @Autowired
  ObjectMapper objectMapper

  @Delegate
  FiatSystemTestSupport fiatIntegrationTestSupport = new FiatSystemTestSupport()

  MockMvc mockMvc;

  def setup() {
    HystrixPlugins.reset();
    this.mockMvc = MockMvcBuilders
        .webAppContextSetup(this.wac)
        .defaultRequest(get("/").content().contentType("application/json"))
        .build();
  }

  def "should get user from repo via endpoint"() {
    setup:
    permissionsRepository.put(unrestrictedUser)
    permissionsRepository.put(roleAUser)
    permissionsRepository.put(roleBUser)
    permissionsRepository.put(roleAroleBUser)

    when:
    def expected = objectMapper.writeValueAsString(unrestrictedUser.view)

    then:
    mockMvc.perform(get("/authorize/anonymous"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))

    when:
    expected = objectMapper.writeValueAsString(roleAUser.merge(unrestrictedUser).view)

    then:
    mockMvc.perform(get("/authorize/roleAUser"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))

    when:
    expected = objectMapper.writeValueAsString(roleBUser.merge(unrestrictedUser).view)

    then:
    mockMvc.perform(get("/authorize/roleBUser"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))

    when:
    expected = objectMapper.writeValueAsString(roleAroleBUser.merge(unrestrictedUser).view)

    then:
    mockMvc.perform(get("/authorize/roleAroleBUser"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))

    when:
    fiatServerConfigurationProperties.setGetAllEnabled(false)


    then:
    mockMvc.perform(get("/authorize/")).andExpect(status().is4xxClientError())

    when:
    fiatServerConfigurationProperties.setGetAllEnabled(true)
    // This only works because we made a bunch of .merge(unrestrictedUser) calls that
    // added the unrestricted resources to the users.
    expected = objectMapper.writeValueAsString([unrestrictedUser.view,
                                                roleAUser.view,
                                                roleBUser.view,
                                                roleAroleBUser.view])

    then:
    mockMvc.perform(get("/authorize/"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))
  }


  def "should get user from repo"() {
    setup:
    PermissionsRepository repository = Mock(PermissionsRepository)
    AuthorizeController controller = new AuthorizeController(registry, repository, fiatServerConfigurationProperties)

    def foo = new UserPermission().setId("foo@batman.com")

    when:
    controller.getUserPermission("foo%40batman.com")

    then:
    1 * repository.get("foo@batman.com") >> Optional.empty()
    thrown NotFoundException

    when:
    def result = controller.getUserPermission("foo%40batman.com")

    then:
    1 * repository.get("foo@batman.com") >> Optional.of(foo)
    result == foo.view
  }

  def "should get user's accounts from repo"() {
    setup:
    PermissionsRepository repository = Mock(PermissionsRepository)
    AuthorizeController controller = new AuthorizeController(registry, repository, fiatServerConfigurationProperties)

    def bar = new Account().setName("bar")
    def foo = new UserPermission().setId("foo").setAccounts([bar] as Set)

    when:
    controller.getUserAccounts("foo")

    then:
    1 * repository.get("foo") >> Optional.empty()
    thrown NotFoundException

    when:
    def result = controller.getUserAccounts("foo")

    then:
    1 * repository.get("foo") >> Optional.of(foo)
    result == [bar.getView([] as Set, false)] as Set

    when:
    result = controller.getUserAccount("foo", "bar")

    then:
    1 * repository.get("foo") >> Optional.of(foo)
    result == bar.getView([] as Set, false)
  }

  def "should get service accounts from repo"() {
    setup:
    permissionsRepository.put(unrestrictedUser)
    permissionsRepository.put(roleServiceAccountUser)

    when:
    def expected = objectMapper.writeValueAsString([serviceAccount.getView([] as Set, false)])

    then:
    mockMvc.perform(get("/authorize/roleServiceAccountUser/serviceAccounts"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))

    when:
    expected = objectMapper.writeValueAsString(serviceAccount.getView([] as Set, false))

    then:
    mockMvc.perform(get("/authorize/roleServiceAccountUser/serviceAccounts/svcAcct"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))
  }

  def "get list of roles for user"() {
    setup:
    permissionsRepository.put(roleAUser)
    permissionsRepository.put(roleAroleBUser)

    when:
    def expected = objectMapper.writeValueAsString(roleAUser.getRoles()*.getView([] as Set, false))

    then:
    mockMvc.perform(get("/authorize/roleAUser/roles"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))

    when:
    expected = objectMapper.writeValueAsString(roleAroleBUser.getRoles()*.getView([] as Set, false))

    then:
    mockMvc.perform(get("/authorize/roleAroleBUser/roles"))
           .andExpect(status().isOk())
           .andExpect(content().json(expected))
  }

  @Unroll
  def "should fallback to unrestricted user if no session available"() {
    given:
    def authorizeController = new AuthorizeController(
        registry,
        permissionsRepository,
        new FiatServerConfigurationProperties(defaultToUnrestrictedUser: defaultToUnrestrictedUser)
    )
    permissionsRepository.put(unrestrictedUser)

    when:
    Optional<UserPermission> optionalUserPermission

    try {
      MDC.put("X-SPINNAKER-USER", authenticatedUser)
      optionalUserPermission = authorizeController.getUserPermissionOrDefault(targetUser)
    } finally {
      MDC.remove("X-SPINNAKER-USER")
    }

    then:
    optionalUserPermission.orElse(null) == (shouldReturnUnrestrictedUser ? unrestrictedUser.setId(targetUser) : null)

    where:
    authenticatedUser     | targetUser            | defaultToUnrestrictedUser || shouldReturnUnrestrictedUser
    "existing_user"       | "user_does_not_exist" | false                     || false
    "existing_user"       | "user_does_not_exist" | true                      || false
    "user_has_no_session" | "user_has_no_session" | false                     || false
    "user_has_no_session" | "user_has_no_session" | true                      || true
  }
}
