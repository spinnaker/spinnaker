/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.front50.model.application

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

class GoogleApplicationDAOProviderSpec extends Specification {
  @Subject provider = new GoogleApplicationDAOProvider()

  void "should fail when not a GoogleNamedAccountCredentials object"() {
    expect:
      !provider.supports(AccountCredentials)
  }

  void "should return a GoogleApplicationDAO for a GoogleNamedAccountCredentials object"() {
    setup:
      def mockCredentials = Mock(GoogleNamedAccountCredentials)

    when:
      def dao = provider.getForAccount(mockCredentials)

    then:
      dao instanceof GoogleApplicationDAO
  }
}
