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

package com.netflix.spinnaker.orca.clouddriver.config

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
        new DefaultServiceSelector(katoService, 1, [:])
      ]
    )

    when:
    def service = selectableService.getService(criteria)

    then:
    service == expectedService

    where:
    criteria                                                                                    || expectedService
    new SelectableService.Criteria(null, null, null, null)                                      || katoService      // the default service selector
    new SelectableService.Criteria("spindemo", null, "orchestration", "api")                    || mortService
    new SelectableService.Criteria("1-spindemo-1", null, "orchestration", "api")                || mortService
    new SelectableService.Criteria("spindemo", null, "orchestration", "deck")                   || instanceService  // origin selector is higher priority
    new SelectableService.Criteria("spindemo", null, "pipeline", "deck")                        || mortService      // fall back to application selector as origin selector does not support pipeline
    new SelectableService.Criteria("spintest", null, "orchestration", "api")                    || oortService
    new SelectableService.Criteria("spintest", null, "pipeline", "api")                         || katoService
    new SelectableService.Criteria("spintest", "user1@unsupported.com", "orchestration", "api") || oortService
    new SelectableService.Criteria("spintest", "user1@email.com", "orchestration", "api")       || bakeryService    // user selector is highest priority
    new SelectableService.Criteria("spintest", "user2@random.com", "orchestration", "api")      || bakeryService    // user selector supports regex patterns
  }

  def "should default to all execution types if none configured (by origin selector)"() {
    expect:
    new ByOriginServiceSelector(instanceService, 20, [:]).executionTypes.sort() == ["orchestration", "pipeline"]
  }
}
