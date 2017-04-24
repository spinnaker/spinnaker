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
import org.apache.commons.collections4.CollectionUtils
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultServiceAccountProviderSpec extends Specification {

  @Shared
  ServiceAccount aAcct = new ServiceAccount(name: "a", memberOf: ["a"])

  @Shared
  ServiceAccount bAcct = new ServiceAccount(name: "b", memberOf: ["a", "b"])

  @Shared
  Front50Service front50Service = Mock(Front50Service) {
    getAllServiceAccounts() >> [aAcct, bAcct]
  }

  @Subject
  DefaultServiceAccountProvider provider = new DefaultServiceAccountProvider(front50Service)

  @Unroll
  def "should return all accounts the specified groups has access to"() {
    when:
    def result = provider.getAllRestricted(input.collect { new Role(it) } as Set)

    then:
    CollectionUtils.disjunction(result, expected).isEmpty()

    when:
    provider.getAllRestricted(null)

    then:
    thrown IllegalArgumentException

    where:
    input           || expected
    []              || []
    ["a"]           || [aAcct]
    ["b"]           || []
    ["c"]           || []
    ["a", "b"]      || [aAcct, bAcct]
    ["a", "b", "c"] || [aAcct, bAcct]
  }
}
