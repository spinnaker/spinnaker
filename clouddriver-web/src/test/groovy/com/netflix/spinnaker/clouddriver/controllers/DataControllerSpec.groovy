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
import spock.lang.Specification
import spock.lang.Subject

import javax.servlet.http.HttpServletRequest;

class DataControllerSpec extends Specification {
  def dataProvider = Mock(DataProvider) {
    supportsIdentifier(_, _) >> { return true }
    getAccountForIdentifier(_, _) >> { _, id -> return id }
  }

  @Subject
  def dataController = new DataController(dataProviders: [dataProvider])

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

  def "should verify access to account when fetching adhoc data"() {
    given:
    def httpServletRequest = Mock(HttpServletRequest)

    when:
    dataController.getAdhocData("groupId", "restricted", httpServletRequest)

    then:
    thrown(AccessDeniedException)

    when:
    MDC.put(AuthenticatedRequest.Header.ACCOUNTS.header, "restricted")
    dataController.getAdhocData("groupId", "restricted", httpServletRequest)

    then:
    1 * httpServletRequest.getAttribute(_) >> { return "pattern" }
    1 * httpServletRequest.getServletPath() >> { return "/servlet/path" }
    notThrown(AccessDeniedException)
  }
}
