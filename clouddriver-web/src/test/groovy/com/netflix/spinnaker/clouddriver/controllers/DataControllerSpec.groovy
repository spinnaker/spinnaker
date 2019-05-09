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
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.security.access.AccessDeniedException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import javax.servlet.http.HttpServletRequest

class DataControllerSpec extends Specification {

  @Shared
  Optional<List<DataProvider>> dataProviders

  @Subject
  def dataController = new DataController(dataProviders)

  void setupSpec() {
    DataProvider dataProvider = Mock(DataProvider) {
      supportsIdentifier(_ as DataProvider.IdentifierType, _ as String) >> { return true }
      getAccountForIdentifier(_ as DataProvider.IdentifierType, _ as String) >> { _, id -> return id }
    }

    dataProviders = Optional.of([dataProvider])
  }

  void setup() {
    MDC.remove(AuthenticatedRequest.Header.ACCOUNTS.header)
  }


  def "should verify access to account when fetching static data"() {
    when:
    dataController.getStaticData("restricted", [:])

    then:
    thrown(AccessDeniedException)

    when:
    MDC.put(AuthenticatedRequest.Header.ACCOUNTS.header, "restricted")
    dataController.getStaticData("restricted", [:])

    then:
    notThrown(AccessDeniedException)
  }

  def "should deny when fetching adhoc data with no accounts"() {
    given:
    def httpServletRequest = Mock(HttpServletRequest)

    when:
    dataController.getAdhocData("groupId", "restricted", httpServletRequest)

    then:
    thrown(AccessDeniedException)
  }

  def "should allow access to account when fetching adhoc data with correct account"() {
    given:
    def httpServletRequest = Mock(HttpServletRequest)
    MDC.put(AuthenticatedRequest.Header.ACCOUNTS.header, "restricted")

    when:
    dataController.getAdhocData("groupId", "restricted", httpServletRequest)

    then:
    httpServletRequest.getAttribute(_ as String) >> { return "pattern" }
    httpServletRequest.getServletPath() >> { return "/servlet/path" }
    notThrown(AccessDeniedException)
  }

  // If the wrong slf4j is on the classpath, this fails. So leaving this test in here for sanity.
  def "mdc works"() {
    given:
    MDC.put(AuthenticatedRequest.Header.ACCOUNTS.header, "restricted")

    expect:
    MDC.get(AuthenticatedRequest.Header.ACCOUNTS.header) == "restricted"
  }
}
