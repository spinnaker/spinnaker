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

import com.netflix.spinnaker.security.AuthenticatedRequest
import org.apache.log4j.MDC
import spock.lang.Specification

class ExecutionSpec extends Specification {
  void "should return Optional.empty if no authenticated details available"() {
    given:
    MDC.clear()

    expect:
    !Execution.AuthenticationDetails.build().present
  }

  void "should build AuthenticationDetails containing authenticated details"() {
    given:
    MDC.clear()
    MDC.put(AuthenticatedRequest.SPINNAKER_USER, "SpinnakerUser")
    MDC.put(AuthenticatedRequest.SPINNAKER_ACCOUNTS, "Account1,Account2")

    when:
    def authenticationDetails = Execution.AuthenticationDetails.build().get()

    then:
    authenticationDetails.user == "SpinnakerUser"
    authenticationDetails.allowedAccounts == ["Account1", "Account2"] as Set
  }
}
