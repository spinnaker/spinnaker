/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.kork.common.Header
import org.slf4j.MDC
import org.slf4j.helpers.NOPMDCAdapter
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ExecutionSpec extends Specification {
  void setupSpec() {
    if (MDC.getMDCAdapter() instanceof NOPMDCAdapter) {
      throw new IllegalStateException("ExecutionSpec.AuthenticationDetails tests cannot function " +
              "without a real MDC implementation loaded by slf4j")
    }
  }

  def "should return Optional.empty if no authenticated details available"() {
    given:
    MDC.clear()

    expect:
    !Execution.AuthenticationDetails.build().present
  }

  def "should build AuthenticationDetails containing authenticated details"() {
    given:
    MDC.clear()
    MDC.put(Header.USER.header, "SpinnakerUser")
    MDC.put(Header.ACCOUNTS.header, "Account1,Account2")

    when:
    def authenticationDetails = Execution.AuthenticationDetails.build().get()

    then:
    authenticationDetails.user == "SpinnakerUser"
    authenticationDetails.allowedAccounts == ["Account1", "Account2"] as Set
  }

  def "should calculate context from outputs of all stages"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "3"
        requisiteStageRefIds = ["1", "2"]
        outputs["covfefe"] = "covfefe-3"
        outputs["foo"] = "foo-3"
        outputs["baz"] = "baz-3"
      }
      stage {
        refId = "1"
        outputs["foo"] = "foo-1"
        outputs["bar"] = "bar-1"
      }
      stage {
        refId = "2"
        outputs["baz"] = "foo-2"
        outputs["qux"] = "qux-2"
      }
    }

    expect:
    with(pipeline.context) {
      foo == "foo-3"
      bar == "bar-1"
      baz == "baz-3"
      qux == "qux-2"
      covfefe == "covfefe-3"
    }
  }
}
