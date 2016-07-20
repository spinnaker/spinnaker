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

import com.netflix.spinnaker.fiat.model.ServiceAccount
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DefaultServiceAccountProviderSpec extends Specification {

  @Shared
  @Subject
  DefaultServiceAccountProvider provider = new DefaultServiceAccountProvider(
      serviceAccountsByName: [
          "abc": new ServiceAccount(name: "abc"),
          "xyz": new ServiceAccount(name: "xyz")
      ]
  )

  def "should return single account"() {
    expect:
    provider.getAccount("abc").get().name == "abc"
    !provider.getAccount("def").isPresent()
  }

  def "should return all accounts the specified groups has access to"() {
    when:
    def result = provider.getAccounts(input)

    then:
    result*.name.containsAll(values)

    when:
    provider.getAccounts(null)

    then:
    thrown IllegalArgumentException

    where:
    input                 || values
    []                    || []
    ["abc"]               || ["abc"]
    ["abc", "xyz"]        || ["abc", "xyz"]
    ["abc", "xyz", "def"] || ["abc", "xyz"]
  }
}
