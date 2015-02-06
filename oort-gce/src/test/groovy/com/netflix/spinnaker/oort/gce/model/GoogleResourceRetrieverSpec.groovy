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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import spock.lang.Specification

class GoogleResourceRetrieverSpec extends Specification {
  void "credentials are returned keyed by account name"() {
    setup:
      def credentials1 = new GoogleCredentials()
      def credentialsStub1 = new GoogleNamedAccountCredentials(null, null, null, null) {
        @Override
        String getName() {
          return "account-1"
        }

        @Override
        GoogleCredentials getCredentials() {
          return credentials1
        }
      }

      def credentials2a = new GoogleCredentials()
      def credentialsStub2a = new GoogleNamedAccountCredentials(null, null, null, null) {
        @Override
        String getName() {
          return "account-2"
        }

        @Override
        GoogleCredentials getCredentials() {
          return credentials2a
        }
      }

      def credentials2b = new GoogleCredentials()
      def credentialsStub2b = new GoogleNamedAccountCredentials(null, null, null, null) {
        @Override
        String getName() {
          return "account-2"
        }

        @Override
        GoogleCredentials getCredentials() {
          return credentials2b
        }
      }

      def accountCredentialsProviderMock = Mock(AccountCredentialsProvider)
      accountCredentialsProviderMock.getAll() >> ([credentialsStub1, credentialsStub2a, credentialsStub2b] as Set)

    when:
      def credentialsMap =
        new GoogleResourceRetriever(accountCredentialsProvider: accountCredentialsProviderMock)
          .getAllGoogleCredentialsObjects()

    then:
      credentialsMap.keySet() == ["account-1", "account-2"] as Set
      credentialsMap["account-1"] == [credentials1] as Set
      credentialsMap["account-2"] == [credentials2a, credentials2b] as Set
  }

  void "cache update is skipped when cacheLock cannot be obtained"() {
    setup:
      def googleResourceRetriever = new GoogleResourceRetriever()
      def originalAppMap = googleResourceRetriever.applicationsMap

    when:
      googleResourceRetriever.cacheLock.lock()

      // Need to attempt to acquire the lock in a different thread (lock is reentrant).
      Thread.start {
        googleResourceRetriever.handleCacheUpdate([serverGroupName: "testapp-dev-v000", account: "account-1"])
      }.join()

    then:
      googleResourceRetriever.applicationsMap.is(originalAppMap)
  }

  void "new applications map is created on update"() {
    setup:
      def googleResourceRetriever = new GoogleResourceRetriever()
      def originalAppMap = googleResourceRetriever.applicationsMap

    when:
      googleResourceRetriever.createUpdatedApplicationMap("account-1", "testapp-dev-v000", null)

    then:
      !googleResourceRetriever.applicationsMap.is(originalAppMap)
  }

  void "old server group is removed on update"() {
    setup:
      def googleResourceRetriever = new GoogleResourceRetriever()
      def cluster =
        Utils.retrieveOrCreatePathToCluster(googleResourceRetriever.applicationsMap, "account-1", "testapp", "testapp-dev")

    when:
      cluster.serverGroups << new GoogleServerGroup(name: "testapp-dev-v000")
      cluster.serverGroups << new GoogleServerGroup(name: "testapp-dev-v001")

    then:
      cluster.serverGroups.collect { serverGroup ->
        serverGroup.name
      } as Set == ["testapp-dev-v000", "testapp-dev-v001"] as Set

    when:
      googleResourceRetriever.createUpdatedApplicationMap("account-1", "testapp-dev-v000", null)
      cluster =
        Utils.retrieveOrCreatePathToCluster(googleResourceRetriever.applicationsMap, "account-1", "testapp", "testapp-dev")

    then:
      cluster.serverGroups.collect { serverGroup ->
        serverGroup.name
      } == ["testapp-dev-v001"]
  }

  void "new server group is added on update"() {
    setup:
      def googleResourceRetriever = new GoogleResourceRetriever()
      def cluster =
        Utils.retrieveOrCreatePathToCluster(googleResourceRetriever.applicationsMap, "account-1", "testapp", "testapp-dev")

    when:
      cluster.serverGroups << new GoogleServerGroup(name: "testapp-dev-v000", zones: ["us-central1-a"])
      cluster.serverGroups << new GoogleServerGroup(name: "testapp-dev-v001")

    then:
      cluster.serverGroups.collect { serverGroup ->
        serverGroup.name
      } as Set == ["testapp-dev-v000", "testapp-dev-v001"] as Set
      cluster.serverGroups.find { serverGroup ->
        serverGroup.name == "testapp-dev-v000"
      }.zones == ["us-central1-a"]

    when:
      googleResourceRetriever.createUpdatedApplicationMap(
        "account-1", "testapp-dev-v000", new GoogleServerGroup(name: "testapp-dev-v000", zones: ["us-central1-f"]))
      cluster =
        Utils.retrieveOrCreatePathToCluster(googleResourceRetriever.applicationsMap, "account-1", "testapp", "testapp-dev")

    then:
      cluster.serverGroups.collect { serverGroup ->
        serverGroup.name
      } as Set == ["testapp-dev-v000", "testapp-dev-v001"] as Set
      cluster.serverGroups.find { serverGroup ->
        serverGroup.name == "testapp-dev-v000"
      }.zones == ["us-central1-f"]
  }
}
