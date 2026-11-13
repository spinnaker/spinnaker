/*
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.model.DataProvider
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.TestingAuthenticationToken
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import jakarta.servlet.http.HttpServletRequest

class DataControllerSpec extends Specification {

  @Shared
  Optional<List<DataProvider>> dataProviders

  FiatPermissionEvaluator permissionEvaluator = Mock(FiatPermissionEvaluator)

  @Subject
  DataController dataController

  void setupSpec() {
    DataProvider dataProvider = Mock(DataProvider) {
      supportsIdentifier(_ as DataProvider.IdentifierType, _ as String) >> { return true }
      getAccountForIdentifier(_ as DataProvider.IdentifierType, _ as String) >> { _, id -> return id }
    }

    dataProviders = Optional.of([dataProvider])
  }

  void setup() {
    dataController = new DataController(dataProviders, permissionEvaluator)
    SecurityContextHolder.context.authentication = new TestingAuthenticationToken("user", "pw")
  }

  void cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "should verify access to account via fiat when fetching static data"() {
    when:
    dataController.getStaticData("restricted", [:])

    then:
    1 * permissionEvaluator.hasPermission(_ as Authentication, "restricted", "ACCOUNT", "READ") >> false
    thrown(AccessDeniedException)

    when:
    dataController.getStaticData("restricted", [:])

    then:
    1 * permissionEvaluator.hasPermission(_ as Authentication, "restricted", "ACCOUNT", "READ") >> true
    notThrown(AccessDeniedException)
  }

  def "should ignore the accounts header when fetching static data"() {
    given:
    AuthenticatedRequest.set("X-SPINNAKER-ACCOUNTS", "restricted")

    when:
    dataController.getStaticData("restricted", [:])

    then:
    1 * permissionEvaluator.hasPermission(_ as Authentication, "restricted", "ACCOUNT", "READ") >> false
    thrown(AccessDeniedException)

    cleanup:
    AuthenticatedRequest.clear()
  }

  def "should deny when fetching adhoc data without account access"() {
    given:
    def httpServletRequest = Mock(HttpServletRequest)

    when:
    dataController.getAdhocData("groupId", "restricted", httpServletRequest)

    then:
    1 * permissionEvaluator.hasPermission(_ as Authentication, "restricted", "ACCOUNT", "READ") >> false
    thrown(AccessDeniedException)
  }

  def "should allow access when fetching adhoc data with account access"() {
    given:
    def httpServletRequest = Mock(HttpServletRequest)

    when:
    dataController.getAdhocData("groupId", "restricted", httpServletRequest)

    then:
    1 * permissionEvaluator.hasPermission(_ as Authentication, "restricted", "ACCOUNT", "READ") >> true
    httpServletRequest.getAttribute(_ as String) >> { return "pattern" }
    httpServletRequest.getServletPath() >> { return "/servlet/path" }
    notThrown(AccessDeniedException)
  }
}
