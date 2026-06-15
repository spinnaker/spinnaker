/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Function
import com.netflix.spinnaker.clouddriver.model.FunctionProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class FunctionControllerSpec extends Specification {

  @Shared
  FunctionProvider provider1

  @Subject
  FunctionController controller

  def setup() {
    provider1 = Mock(FunctionProvider)
    controller = new FunctionController(Optional.of([provider1]))
  }

  void "list with no parameters calls unfiltered getAllFunctions"() {
    when:
    controller.list(null, null, null)

    then:
    1 * provider1.getAllFunctions() >> []
    0 * provider1.getAllFunctions(_, _)
  }

  void "list with account parameter calls filtered getAllFunctions"() {
    given:
    def account = "prod"

    when:
    controller.list(null, null, account)

    then:
    1 * provider1.getAllFunctions(account, null) >> []
    0 * provider1.getAllFunctions()
  }

  void "list with region parameter calls filtered getAllFunctions"() {
    given:
    def region = "us-east-1"

    when:
    controller.list(null, region, null)

    then:
    1 * provider1.getAllFunctions(null, region) >> []
    0 * provider1.getAllFunctions()
  }

  void "list with both account and region parameters calls filtered getAllFunctions"() {
    given:
    def account = "prod"
    def region = "us-east-1"

    when:
    controller.list(null, region, account)

    then:
    1 * provider1.getAllFunctions(account, region) >> []
    0 * provider1.getAllFunctions()
  }

  void "list with empty functionName and account calls filtered getAllFunctions"() {
    given:
    def account = "prod"

    when:
    controller.list("", null, account)

    then:
    1 * provider1.getAllFunctions(account, null) >> []
    0 * provider1.getAllFunctions()
  }

  void "list filters out null results when getting specific function"() {
    given:
    def functionName = "myFunction"
    def function1 = Mock(Function)

    when:
    def result = controller.list(functionName, null, null)

    then:
    1 * provider1.getFunction(null, null, functionName) >> function1
    result == [function1]
  }

  void "performance: filtered getAllFunctions returns subset of data"() {
    given:
    def account = "prod"
    def region = "us-east-1"

    // Simulate unfiltered call returning many functions from multiple accounts
    def allFunctions = (1..1000).collect { i ->
      Mock(Function) {
        getAccount() >> (i % 10 == 0 ? "prod" : "dev-${i % 10}")
        getRegion() >> (i % 5 == 0 ? "us-east-1" : "us-west-${i % 5}")
      }
    }

    // Filtered call returns only matching functions (much smaller set)
    def filteredFunctions = (1..100).collect { i ->
      Mock(Function) {
        getAccount() >> "prod"
        getRegion() >> "us-east-1"
      }
    }

    when: "calling with account filter"
    def result = controller.list(null, region, account)

    then: "only filtered method is called, returning smaller dataset"
    1 * provider1.getAllFunctions(account, region) >> filteredFunctions
    0 * provider1.getAllFunctions() >> allFunctions // Unfiltered method NOT called
    result.size() == 100 // Much smaller than the 1000 that would be fetched unfiltered
  }

  void "performance: demonstrates timing improvement with filtered calls"() {
    given:
    def account = "prod"
    def slowLoadTime = 100 // milliseconds
    def fastLoadTime = 10  // milliseconds

    def function = Mock(Function)

    when: "calling unfiltered endpoint (slow path)"
    def unfilteredStart = System.currentTimeMillis()
    controller.list(null, null, null)
    def unfilteredTime = System.currentTimeMillis() - unfilteredStart

    then: "unfiltered call simulates slow load of all data"
    1 * provider1.getAllFunctions() >> {
      Thread.sleep(slowLoadTime)
      return [function]
    }

    when: "calling with account filter (fast path)"
    def filteredStart = System.currentTimeMillis()
    controller.list(null, null, account)
    def filteredTime = System.currentTimeMillis() - filteredStart

    then: "filtered call is significantly faster"
    1 * provider1.getAllFunctions(account, null) >> {
      Thread.sleep(fastLoadTime)
      return [function]
    }

    and: "filtered call is at least 5x faster"
    filteredTime < (unfilteredTime / 5)
  }

  void "controller handles empty provider list"() {
    given:
    def emptyController = new FunctionController(Optional.empty())

    when:
    def result = emptyController.list(null, null, "prod")

    then:
    result == []
    notThrown(Exception)
  }

  void "performance: verifies correct method called for various parameter combinations"() {
    expect: "filtered method is called when at least one filter is present"
    when:
    controller.list(null, region, account)

    then:
    callsFiltered * provider1.getAllFunctions(account, region) >> []
    callsUnfiltered * provider1.getAllFunctions() >> []

    where:
    account | region       | callsFiltered | callsUnfiltered
    null    | null         | 0             | 1              // No filters: slow path
    "prod"  | null         | 1             | 0              // Account filter: fast path
    null    | "us-east-1"  | 1             | 0              // Region filter: fast path
    "prod"  | "us-east-1"  | 1             | 0              // Both filters: fast path
  }
}
