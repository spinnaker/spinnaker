/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.security

import org.slf4j.MDC
import spock.lang.Specification

class AuthenticatedRequestSpec extends Specification {
  void "should extract user details by priority (Principal > MDC)"() {
    when:
    MDC.clear()
    MDC.put(AuthenticatedRequest.SPINNAKER_USER, "spinnaker-user")

    then:
    AuthenticatedRequest.getSpinnakerUser().get() == "spinnaker-user"
    AuthenticatedRequest.getSpinnakerUser(new User([email: "spinnaker-other"])).get() == "spinnaker-other"
  }

  void "should extract allowed account details by priority (Principal > MDC"() {
    when:
    MDC.clear()
    MDC.put(AuthenticatedRequest.SPINNAKER_ACCOUNTS, "account1,account2")

    then:
    AuthenticatedRequest.getSpinnakerAccounts().get() == "account1,account2"
    AuthenticatedRequest.getSpinnakerAccounts(new User(allowedAccounts: ["account3", "account4"])).get() == "account3,account4"
  }

  void "should have no user/allowed account details if no MDC or Principal available"() {
    when:
    MDC.clear()

    then:
    !AuthenticatedRequest.getSpinnakerUser().present
    !AuthenticatedRequest.getSpinnakerAccounts().present
  }

  void "should propagate user/allowed account details"() {
    when:
    MDC.put(AuthenticatedRequest.SPINNAKER_USER, "spinnaker-user")
    MDC.put(AuthenticatedRequest.SPINNAKER_ACCOUNTS, "account1,account2")

    def closure = AuthenticatedRequest.propagate({
      assert AuthenticatedRequest.getSpinnakerUser().get() == "spinnaker-user"
      assert AuthenticatedRequest.getSpinnakerAccounts().get() == "account1,account2"
      return true
    })

    MDC.put(AuthenticatedRequest.SPINNAKER_USER, "spinnaker-another-user")
    MDC.put(AuthenticatedRequest.SPINNAKER_ACCOUNTS, "account1,account3")

    then:
    closure.call()

    // ensure MDC context is restored
    MDC.get(AuthenticatedRequest.SPINNAKER_USER) == "spinnaker-another-user"
    MDC.get(AuthenticatedRequest.SPINNAKER_ACCOUNTS) == "account1,account3"

    when:
    MDC.clear()

    then:
    closure.call()
    MDC.clear()
  }
}
