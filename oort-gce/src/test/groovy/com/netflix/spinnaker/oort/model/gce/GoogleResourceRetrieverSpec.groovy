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

package com.netflix.spinnaker.oort.model.gce

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.gce.model.GoogleResourceRetriever
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import spock.lang.Specification

class GoogleResourceRetrieverSpec extends Specification {
  void "credentials are returned keyed by account name"() {
    setup:
      def credentials1 = new GoogleCredentials()
      def credentialsMock1 = Mock(GoogleNamedAccountCredentials)
      credentialsMock1.getName() >> "account-1"
      credentialsMock1.getCredentials() >> credentials1

      def credentials2a = new GoogleCredentials()
      def credentialsMock2a = Mock(GoogleNamedAccountCredentials)
      credentialsMock2a.getName() >> "account-2"
      credentialsMock2a.getCredentials() >> credentials2a

      def credentials2b = new GoogleCredentials()
      def credentialsMock2b = Mock(GoogleNamedAccountCredentials)
      credentialsMock2b.getName() >> "account-2"
      credentialsMock2b.getCredentials() >> credentials2b

      def accountCredentialsProviderMock = Mock(AccountCredentialsProvider)
      accountCredentialsProviderMock.getAll() >> ([credentialsMock1, credentialsMock2a, credentialsMock2b] as Set)

    when:
      def credentialsMap = GoogleResourceRetriever.getAllGoogleCredentialsObjects(accountCredentialsProviderMock)

    then:
      credentialsMap.keySet() == ["account-1", "account-2"] as Set
      credentialsMap["account-1"] == [credentials1] as Set
      credentialsMap["account-2"] == [credentials2a, credentials2b] as Set
  }
}
