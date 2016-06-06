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

package com.netflix.spinnaker.gate.security.anonymous

import com.netflix.spinnaker.gate.services.CredentialsService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.CopyOnWriteArrayList

class AnonymousConfigSpec extends Specification {

  @Unroll
  def "should update accounts correctly"() {
    setup:
      CredentialsService credentialsService = Mock(CredentialsService) {
        getAccountNames(*_) >> newAccounts
      }
      @Subject
      AnonymousConfig config = new AnonymousConfig(
          anonymousAllowedAccounts: new CopyOnWriteArrayList<String>(oldAccounts),
          credentialsService: credentialsService
      )

    when:
      config.updateAnonymousAccounts()

    then:
      config.anonymousAllowedAccounts == newAccounts

    where:
      oldAccounts || newAccounts
      []          || ["a"]
      ["a"]       || ["a", "b"]
      ["a"]       || ["b"]
      ["a"]       || []
  }
}
