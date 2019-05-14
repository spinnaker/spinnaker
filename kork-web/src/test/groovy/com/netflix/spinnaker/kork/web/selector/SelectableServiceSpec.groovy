/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.selector

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll;

class SelectableServiceSpec extends Specification {
  @Shared
  def mortService = "mort"

  @Shared
  def oortService = "oort"

  @Shared
  def katoService = "kato"

  @Shared
  def instanceService = "instance"

  @Shared
  def bakeryService = "bakery"

  @Unroll
  def "should lookup service by application or executionType"() {
    given:
    def selectableService = new SelectableService(
      [
        new ByApplicationServiceSelector(mortService, 10, ["applicationPattern": ".*spindemo.*"]),
        new ByExecutionTypeServiceSelector(oortService, 5, ["executionTypes": [0: "orchestration"]]),
        new ByOriginServiceSelector(instanceService, 20, ["origin": "deck", "executionTypes": [0: "orchestration"]]),
        new ByAuthenticatedUserServiceSelector(bakeryService, 25, ["users": [0: "user1@email.com", 1: ".*user2.*"]]),
        new DefaultServiceSelector(katoService, 1, [:]),
        new ByLocationServiceSelector(bakeryService, 10, ["locations": [0: "us-west-1", 1: "us-east-1"]])
      ]
    )

    when:
    def service = selectableService.getService(criteriaWithParams(selectorParams))

    then:
    service == expectedService

    where:
    selectorParams                                                                                                       || expectedService
    [:]                                                                                                                  || katoService      // the default service selector
    [application: "spindemo", executionType: "orchestration", origin: "api"]                                             || mortService
    [application: "1-spindemo-1", executionType: "orchestration", origin: "api"]                                         || mortService
    [application: "1-spindemo-1", executionType: "orchestration", origin: "deck"]                                        || instanceService  // origin selector is higher priority
    [application: "spindemo", executionType: "pipeline", origin: "deck"]                                                 || mortService      // fall back to application selector as origin selector does not support pipeline
    [application: "spintest", executionType: "orchestration", origin: "api"]                                             || oortService
    [application: "spintest", executionType: "pipeline", origin: "api"]                                                  || katoService
    [application: "spintest", executionType: "orchestration", origin: "api", authenticatedUser: "user1@unsupported.com"] || oortService
    [application: "spintest", executionType: "orchestration", origin: "api", authenticatedUser: "user1@unsupported.com"] || oortService
    [application: "spintest", executionType: "orchestration", origin: "api", authenticatedUser: "user1@email.com"]       || bakeryService    // user selector is highest priority
    [application: "spintest", executionType: "orchestration", origin: "api", authenticatedUser: "user2@random.com"]      || bakeryService    // user selector is highest priority
    [location: "us-east-1"]                                                                                              || bakeryService    // selects by location
  }

  def "should default to all execution types if none configured (by origin selector)"() {
    expect:
    new ByOriginServiceSelector(instanceService, 20, [:]).executionTypes.sort() == ["orchestration", "pipeline"]
  }

  private static SelectableService.Criteria criteriaWithParams(Map<String, String> params) {
    return new SelectableService.Criteria()
      .withApplication(params.application)
      .withAuthenticatedUser(params.authenticatedUser)
      .withOrigin(params.origin)
      .withExecutionType(params.executionType)
      .withExecutionId(params.executionId)
      .withLocation(params.location)
  }
}
