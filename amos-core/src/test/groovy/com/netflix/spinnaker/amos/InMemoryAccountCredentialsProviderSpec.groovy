/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class InMemoryAccountCredentialsProviderSpec extends Specification {

  @Subject provider = new InMemoryAccountCredentialsProvider()

  @Shared credentials = Mock(AccountCredentials) {
    getName() >> "foo"
  }

  void "should be able to get the names of all stored credentials"() {
    given:
      provider.put(credentials)

    expect:
      provider.accountNames == ["foo"] as Set
  }

  void "should be able to retrieve credentials by name"() {
    setup:
      provider.put(credentials)

    when:
      def creds = provider.getCredentials("foo")

    then:
      creds.is credentials
  }
}
