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

package com.netflix.spinnaker.fiat.providers

import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DefaultServiceAccountProviderSpec extends Specification {

  @Shared
  ServiceAccount abcAcct = new ServiceAccount(name: "abc")

  @Shared
  ServiceAccount xyzAcct = new ServiceAccount(name: "xyz@domain.com")

  @Shared
  Front50Service front50Service = Mock(Front50Service) {
    getAllServiceAccounts() >> [abcAcct, xyzAcct]
  }

  @Subject
  DefaultServiceAccountProvider provider = new DefaultServiceAccountProvider(
      front50Service: front50Service
  )

  def "should return all accounts the specified groups has access to"() {
    when:
    def result = provider.getAllRestricted(input.collect {new Role(it)})

    then:
    result.containsAll(values)

    when:
    provider.getAllRestricted(null)

    then:
    thrown IllegalArgumentException

    where:
    input                 || values
    []                    || []
    ["abc"]               || [abcAcct]
    ["abc", "xyz"]        || [abcAcct, xyzAcct]
    ["abc", "xyz", "def"] || [abcAcct, xyzAcct]
  }
}
