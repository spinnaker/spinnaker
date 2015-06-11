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
import com.netflix.spinnaker.oort.gce.model.callbacks.InstanceAggregatedListCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import com.netflix.spinnaker.oort.model.HealthState
import spock.lang.Specification
import spock.lang.Subject

class GoogleResourceRetrieverSpec extends Specification {
  void "credentials are returned keyed by account name"() {
    setup:
      def credentials1 = new GoogleCredentials()
      def credentialsStub1 = new GoogleNamedAccountCredentials(null, null, null) {
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
      def credentialsStub2a = new GoogleNamedAccountCredentials(null, null, null) {
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
      def credentialsStub2b = new GoogleNamedAccountCredentials(null, null, null) {
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

  void "server group summaries are set on load balancers and only include instances registered with load balancers"() {
    setup:
      def appMap = buildTestAppMap1()
      def networkLoadBalancerMap = buildNetworkLoadBalancerMap()
      @Subject
      def googleResourceRetriever = new GoogleResourceRetriever()

    when:
      googleResourceRetriever.populateLoadBalancerServerGroups(appMap, networkLoadBalancerMap)

    then:
      networkLoadBalancerMap == buildNetworkLoadBalancerMapWithServerGroupSummaries()
  }

  void "load balancer health states are migrated to new server group"() {
    setup:
      def googleResourceRetriever = new GoogleResourceRetriever()

      // Build path from cluster through original server group and instance.
      def origGoogleCluster =
        Utils.retrieveOrCreatePathToCluster(googleResourceRetriever.applicationsMap, "account-1", "testapp", "testapp-dev")
      def origGoogleServerGroup = new GoogleServerGroup(name: "testapp-dev-v000")
      def origGoogleInstance = new GoogleInstance("testapp-dev-v000-abcd")
      origGoogleInstance.setProperty("health", [
        buildGCEHealthState(HealthState.Unknown),
        buildLoadBalancerHealthState(HealthState.Up, "testapp-dev-frontend2", "testapp-dev-v000-abcd", "InService")
      ])
      origGoogleServerGroup.instances << origGoogleInstance
      origGoogleCluster.serverGroups << origGoogleServerGroup

      // Build new server group and instance.
      def newGoogleServerGroup = new GoogleServerGroup(name: "testapp-dev-v000")
      def newGoogleInstance = new GoogleInstance("testapp-dev-v000-abcd")
      newGoogleInstance.setProperty("health", [
        buildGCEHealthState(HealthState.Unknown)
      ])
      newGoogleServerGroup.instances << newGoogleInstance

    when:
      def isHealthy = InstanceAggregatedListCallback.calculateIsHealthy(newGoogleInstance)

    then:
      // As there are no Up health states, the instance should be considered unhealthy.
      !isHealthy

    when:
      googleResourceRetriever.migrateInstanceLoadBalancerHealthStates(newGoogleServerGroup, "account-1", "testapp", "testapp-dev")
      isHealthy = InstanceAggregatedListCallback.calculateIsHealthy(newGoogleInstance)

    then:
      // As there is now an Up load balancer health states, the instance should be considered healthy.
      isHealthy

      // Retrieve the instance from the server group to ensure the correct instance was updated.
      def googleInstance = newGoogleServerGroup.instances.find { googleInstance ->
        googleInstance.name == "testapp-dev-v000-abcd"
      }

      // Verify the presence of the load balancer health state.
      def loadBalancerHealthState = googleInstance.getProperty("health").find { healthState ->
        healthState.type == "LoadBalancer"
      }

      loadBalancerHealthState?.state == HealthState.Up
  }

  void "load balancer health states are not migrated to new server group when no matching instances are found"() {
    setup:
      def googleResourceRetriever = new GoogleResourceRetriever()

      // Build path from cluster through original server group and instance.
      def origGoogleCluster =
        Utils.retrieveOrCreatePathToCluster(googleResourceRetriever.applicationsMap, "account-1", "testapp", "testapp-dev")
      def origGoogleServerGroup = new GoogleServerGroup(name: "testapp-dev-v000")
      def origGoogleInstance = new GoogleInstance("testapp-dev-v000-efgh")
      origGoogleInstance.setProperty("health", [
        buildGCEHealthState(HealthState.Unknown),
        buildLoadBalancerHealthState(HealthState.Up, "testapp-dev-frontend2", "testapp-dev-v000-efgh", "InService")
      ])
      origGoogleServerGroup.instances << origGoogleInstance
      origGoogleCluster.serverGroups << origGoogleServerGroup

      // Build new server group and instance.
      def newGoogleServerGroup = new GoogleServerGroup(name: "testapp-dev-v000")
      def newGoogleInstance = new GoogleInstance("testapp-dev-v000-abcd")
      newGoogleInstance.setProperty("health", [
        buildGCEHealthState(HealthState.Unknown)
      ])
      newGoogleServerGroup.instances << newGoogleInstance

    when:
      def isHealthy = InstanceAggregatedListCallback.calculateIsHealthy(newGoogleInstance)

    then:
      // As there are no Up health states, the instance should be considered unhealthy.
      !isHealthy

    when:
      googleResourceRetriever.migrateInstanceLoadBalancerHealthStates(newGoogleServerGroup, "account-1", "testapp", "testapp-dev")
      isHealthy = InstanceAggregatedListCallback.calculateIsHealthy(newGoogleInstance)

    then:
      // As there are still no Up health states, the instance should still be considered unhealthy.
      !isHealthy

      // Retrieve the instance from the server group.
      def googleInstance = newGoogleServerGroup.instances.find { googleInstance ->
        googleInstance.name == "testapp-dev-v000-abcd"
      }

      // Verify the absence of any load balancer health state.
      def loadBalancerHealthState = googleInstance.getProperty("health").find { healthState ->
        healthState.type == "LoadBalancer"
      }

      !loadBalancerHealthState
  }

  private HashMap<String, GoogleApplication> buildTestAppMap1() {
    def appMap = new HashMap<String, GoogleApplication>()
    def appName1 = "roscoapp1"
    def app1 = new GoogleApplication(name: appName1)
    app1.clusterNames["some-account-name"] = new HashSet<String>()
    app1.clusters["some-account-name"] = new HashMap<String, GoogleCluster>()
    def cluster1 = new GoogleCluster(name: "roscoapp1-dev", accountName: "some-account-name")
    app1.clusters["some-account-name"]["roscoapp1-dev"] = cluster1
    def serverGroup1 = new GoogleServerGroup("roscoapp1-dev-v001", "gce", "us-central1")
    serverGroup1.setProperty("asg", [loadBalancerNames: ["roscoapp-dev-frontend2"]])
    serverGroup1.setDisabled(false)
    def instance1 = new GoogleInstance("roscoapp1-dev-v001-abcd")
    instance1.setProperty("placement", [availabilityZone: "us-central1-a"])
    instance1.setProperty("health", [
      buildGCEHealthState(HealthState.Unknown),
      buildLoadBalancerHealthState(HealthState.Up, "roscoapp-dev-frontend2", "roscoapp1-dev-v001-abcd", "InService")
    ])
    serverGroup1.instances << instance1
    def instance2 = new GoogleInstance("roscoapp1-dev-v001-efgh")
    instance2.setProperty("placement", [availabilityZone: "us-central1-a"])
    serverGroup1.instances << instance2
    def instance3 = new GoogleInstance("roscoapp1-dev-v001-ijkl")
    instance3.setProperty("placement", [availabilityZone: "us-central1-a"])
    instance3.setProperty("health", [
      buildGCEHealthState(HealthState.Unknown),
      buildLoadBalancerHealthState(HealthState.Up, "roscoapp-dev-frontend2", "roscoapp1-dev-v001-ijkl", "InService")
    ])
    serverGroup1.instances << instance3
    cluster1.serverGroups << serverGroup1
    appMap[appName1] = app1
    def appName2 = "roscoapp2"
    def app2 = new GoogleApplication(name: appName2)
    app2.clusterNames["other-account-name"] = new HashSet<String>()
    app2.clusters["other-account-name"] = new HashMap<String, GoogleCluster>()
    def cluster2a = new GoogleCluster(name: "roscoapp2-dev", accountName: "other-account-name")
    app2.clusters["other-account-name"]["roscoapp2-dev"] = cluster2a
    def cluster2b = new GoogleCluster(name: "roscoapp2-test", accountName: "other-account-name")
    app2.clusters["other-account-name"]["roscoapp2-test"] = cluster2b
    appMap[appName2] = app2
    appMap
  }

  private Map<String, Map<String, List<GoogleLoadBalancer>>> buildNetworkLoadBalancerMap() {
    [
      "some-account-name": [
        "us-central1": [
          [
            name: "roscoapp-dev-frontend1",
            type: "gce",
            region: "us-central1",
            account: "some-account-name"
          ],
          [
            name: "roscoapp-dev-frontend2",
            type: "gce",
            region: "us-central1",
            account: "some-account-name",
            serverGroups: [],
            instanceNames: ["roscoapp1-dev-v001-abcd", "roscoapp1-dev-v001-ijkl"]
          ],
          [
            name: "roscoapp-dev-frontend3",
            type: "gce",
            region: "us-central1",
            account: "some-account-name"
          ]
        ],
      ]
    ]
  }

  private Map<String, Map<String, List<GoogleLoadBalancer>>> buildNetworkLoadBalancerMapWithServerGroupSummaries() {
    [
      "some-account-name": [
        "us-central1": [
          [
            name: "roscoapp-dev-frontend1",
            type: "gce",
            region: "us-central1",
            account: "some-account-name"
          ],
          [
            name: "roscoapp-dev-frontend2",
            type: "gce",
            region: "us-central1",
            account: "some-account-name",
            serverGroups: [
              [
                name: "roscoapp1-dev-v001",
                isDisabled: false,
                instances: [
                  [
                    id: "roscoapp1-dev-v001-abcd",
                    zone: "us-central1-a",
                    health: [
                      state: "InService",
                      description: null
                    ]
                  ],
                  [
                    id: "roscoapp1-dev-v001-ijkl",
                    zone: "us-central1-a",
                    health: [
                      state: "InService",
                      description: null
                    ]
                  ]
                ] as Set
              ]
            ],
            instanceNames: ["roscoapp1-dev-v001-abcd", "roscoapp1-dev-v001-ijkl"]
          ],
          [
            name: "roscoapp-dev-frontend3",
            type: "gce",
            region: "us-central1",
            account: "some-account-name"
          ]
        ]
      ]
    ]
  }

  private Map buildGCEHealthState(HealthState healthState) {
    [
      type: "GCE",
      state: healthState
    ]
  }

  private Map buildLoadBalancerHealthState(HealthState healthState, String loadBalancerName, String instanceId, String instanceState) {
      [
        type: "LoadBalancer",
        state: healthState,
        loadBalancers: [
          [
            loadBalancerName: loadBalancerName,
            instanceId: instanceId,
            state: instanceState
          ]
        ],
        instanceId: instanceId
      ]
  }
}
