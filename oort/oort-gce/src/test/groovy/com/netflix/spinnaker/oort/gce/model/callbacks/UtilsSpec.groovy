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

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.netflix.spinnaker.oort.gce.model.GoogleApplication
import com.netflix.spinnaker.oort.gce.model.GoogleCluster
import com.netflix.spinnaker.oort.gce.model.GoogleInstance
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import spock.lang.Specification

class UtilsSpec extends Specification {

  private final static String ACCOUNT_NAME = "test-account"
  private final static String APPLICATION_NAME = "testapp"
  private final static String CLUSTER_DEV_NAME = "testapp-dev"
  private final static String CLUSTER_PROD_NAME = "testapp-prod"
  private final static String SERVER_GROUP_NAME = "testapp-dev-v000"
  private final static String INSTANCE_NAME = "testapp-dev-v000-abcd"
  private final static String LOAD_BALANCER_NAME = "testapp-dev-frontend"
  private final static String REGION = "us-central1"

  void "getImmutableCopy returns an immutable copy"() {
    when:
      def origList = ["abc", "def", "ghi"]
      def copyList = Utils.getImmutableCopy(origList)

    then:
      origList == copyList

    when:
      origList += "jkl"

    then:
      origList != copyList

    when:
      def origMap = [abc: 123, def: 456, ghi: 789]
      def copyMap = Utils.getImmutableCopy(origMap)

    then:
      origMap == copyMap

    when:
      origMap["def"] = 654

    then:
      origMap != copyMap

      Utils.getImmutableCopy(5) == 5
      Utils.getImmutableCopy("some-string") == "some-string"
  }

  void "deep-copied application maps do not share applications, clusters, server groups or load balancers"() {
    setup:
      def originalServerGroup = new GoogleServerGroup(name: SERVER_GROUP_NAME)
      originalServerGroup.instances << new GoogleInstance(name: INSTANCE_NAME)
      def originalClusterDev = new GoogleCluster(name: CLUSTER_DEV_NAME)
      originalClusterDev.serverGroups << originalServerGroup
      originalClusterDev.loadBalancers << new GoogleLoadBalancer(name: LOAD_BALANCER_NAME, account: ACCOUNT_NAME, region: REGION)
      def originalClusterProd = new GoogleCluster(name: CLUSTER_PROD_NAME)
      def originalApplication = new GoogleApplication(name: APPLICATION_NAME)
      originalApplication.clusterNames[ACCOUNT_NAME] = [CLUSTER_DEV_NAME, CLUSTER_PROD_NAME] as Set
      originalApplication.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      originalApplication.clusters[ACCOUNT_NAME][CLUSTER_DEV_NAME] = originalClusterDev
      originalApplication.clusters[ACCOUNT_NAME][CLUSTER_PROD_NAME] = originalClusterProd
      Map<String, GoogleApplication> originalAppMap = new HashMap<String, GoogleApplication>()
      originalAppMap[APPLICATION_NAME] = originalApplication

    when:
      Map<String, GoogleApplication> copyAppMap = Utils.deepCopyApplicationMap(originalAppMap)

    then:
      originalAppMap.keySet() == copyAppMap.keySet()

    when:
      copyAppMap[APPLICATION_NAME].clusterNames[ACCOUNT_NAME] -= CLUSTER_DEV_NAME
      copyAppMap[APPLICATION_NAME].clusters[ACCOUNT_NAME].remove(CLUSTER_DEV_NAME)

    then:
      originalAppMap[APPLICATION_NAME].clusterNames[ACCOUNT_NAME] == [CLUSTER_DEV_NAME, CLUSTER_PROD_NAME] as Set
      copyAppMap[APPLICATION_NAME].clusterNames[ACCOUNT_NAME] == [CLUSTER_PROD_NAME] as Set
      originalAppMap[APPLICATION_NAME].clusters[ACCOUNT_NAME].keySet() == [CLUSTER_DEV_NAME, CLUSTER_PROD_NAME] as Set
      copyAppMap[APPLICATION_NAME].clusters[ACCOUNT_NAME].keySet() == [CLUSTER_PROD_NAME] as Set

    when:
      copyAppMap = Utils.deepCopyApplicationMap(originalAppMap)
      def retrievedOrigClusterDev =
        Utils.retrieveOrCreatePathToCluster(originalAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)
      def retrievedCopyClusterDev =
        Utils.retrieveOrCreatePathToCluster(copyAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)

    then:
      retrievedOrigClusterDev.serverGroups.collect { serverGroup ->
        serverGroup.name
      } == [SERVER_GROUP_NAME]
      retrievedCopyClusterDev.serverGroups.collect { serverGroup ->
        serverGroup.name
      } == [SERVER_GROUP_NAME]

    when:
      def serverGroupToRemove = retrievedCopyClusterDev.serverGroups.find { serverGroup ->
        serverGroup.name == SERVER_GROUP_NAME
      }
      retrievedCopyClusterDev.serverGroups -= serverGroupToRemove

    then:
      retrievedOrigClusterDev.serverGroups.collect { serverGroup ->
        serverGroup.name
      } == [SERVER_GROUP_NAME]
      retrievedCopyClusterDev.serverGroups.collect { serverGroup ->
        serverGroup.name
      } == []

    when:
      copyAppMap = Utils.deepCopyApplicationMap(originalAppMap)
      retrievedOrigClusterDev =
        Utils.retrieveOrCreatePathToCluster(originalAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)
      def retrievedOrigServerGroup = retrievedOrigClusterDev.serverGroups.find { serverGroup ->
        serverGroup.name == SERVER_GROUP_NAME
      }
      retrievedCopyClusterDev =
        Utils.retrieveOrCreatePathToCluster(copyAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)
      def retrievedCopyServerGroup = retrievedCopyClusterDev.serverGroups.find { serverGroup ->
        serverGroup.name == SERVER_GROUP_NAME
      }

    then:
      retrievedOrigServerGroup.instances.collect { instance ->
        instance.name
      } == ["testapp-dev-v000-abcd"]
      retrievedCopyServerGroup.instances.collect { instance ->
        instance.name
      } == ["testapp-dev-v000-abcd"]

    when:
      retrievedCopyServerGroup.instances.clear()

    then:
      retrievedOrigServerGroup.instances.collect { instance ->
        instance.name
      } == ["testapp-dev-v000-abcd"]
      retrievedCopyServerGroup.instances.collect { instance ->
        instance.name
      } == []

    when:
      copyAppMap = Utils.deepCopyApplicationMap(originalAppMap)
      retrievedOrigClusterDev =
        Utils.retrieveOrCreatePathToCluster(originalAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)
      def origLoadBalancer = retrievedOrigClusterDev.loadBalancers.find { it.name == LOAD_BALANCER_NAME }
      retrievedCopyClusterDev =
        Utils.retrieveOrCreatePathToCluster(copyAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)
      def copyLoadBalancer = retrievedCopyClusterDev.loadBalancers.find { it.name == LOAD_BALANCER_NAME }

    then:
      origLoadBalancer.serverGroups == [] as Set
      copyLoadBalancer.serverGroups == [] as Set

    when:
      origLoadBalancer.serverGroups << SERVER_GROUP_NAME

    then:
      origLoadBalancer.serverGroups == [SERVER_GROUP_NAME] as Set
      copyLoadBalancer.serverGroups == [] as Set
  }

  void "cluster paths are retrieved or built as necessary"() {
    setup:
      Map<String, GoogleApplication> originalAppMap = new HashMap<String, GoogleApplication>()

    when:
      GoogleCluster retrievedCluster =
        Utils.retrieveOrCreatePathToCluster(originalAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)

    then:
      retrievedCluster

    when:
      GoogleCluster retrievedAgainCluster =
        Utils.retrieveOrCreatePathToCluster(originalAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)

    then:
      retrievedAgainCluster.is(retrievedCluster)
  }

  void "deep-copied application maps do not lose disabled status of server groups"() {
    setup:
      def originalServerGroup = new GoogleServerGroup(name: SERVER_GROUP_NAME)
      originalServerGroup.instances << new GoogleInstance(name: INSTANCE_NAME)
      originalServerGroup.setDisabled(false)
      def originalClusterDev = new GoogleCluster(name: CLUSTER_DEV_NAME)
      originalClusterDev.serverGroups << originalServerGroup
      originalClusterDev.loadBalancers << new GoogleLoadBalancer(name: LOAD_BALANCER_NAME, account: ACCOUNT_NAME, region: REGION)
      def originalClusterProd = new GoogleCluster(name: CLUSTER_PROD_NAME)
      def originalApplication = new GoogleApplication(name: APPLICATION_NAME)
      originalApplication.clusterNames[ACCOUNT_NAME] = [CLUSTER_DEV_NAME, CLUSTER_PROD_NAME] as Set
      originalApplication.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      originalApplication.clusters[ACCOUNT_NAME][CLUSTER_DEV_NAME] = originalClusterDev
      originalApplication.clusters[ACCOUNT_NAME][CLUSTER_PROD_NAME] = originalClusterProd
      Map<String, GoogleApplication> originalAppMap = new HashMap<String, GoogleApplication>()
      originalAppMap[APPLICATION_NAME] = originalApplication

    when:
      Map<String, GoogleApplication> copyAppMap = Utils.deepCopyApplicationMap(originalAppMap)
      def retrievedCopyClusterDev =
        Utils.retrieveOrCreatePathToCluster(copyAppMap, ACCOUNT_NAME, APPLICATION_NAME, CLUSTER_DEV_NAME)

    then:
      !(retrievedCopyClusterDev.serverGroups.find { it.name == SERVER_GROUP_NAME }.isDisabled())
  }
}
