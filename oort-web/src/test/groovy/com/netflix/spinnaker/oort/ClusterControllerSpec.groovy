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

package com.netflix.spinnaker.oort

import com.netflix.spinnaker.oort.controllers.ClusterController
import com.netflix.spinnaker.oort.model.*
import spock.lang.Shared
import spock.lang.Specification

class ClusterControllerSpec extends Specification {

  @Shared
  ClusterController clusterController

  def setup() {
    clusterController = new ClusterController()
  }

  void "should call all application providers to get and merge the cluster names"() {
    setup:
    def app1 = Stub(Application) {
      getName() >> "app"
      getClusterNames() >> ["test": ["foo", "bar"] as Set]
    }
    def appProvider1 = Stub(ApplicationProvider) {
      getApplication("app") >> app1
    }


    def app2 = Stub(Application) {
      getName() >> "app"
      getClusterNames() >> ["prod": ["baz"] as Set]
    }
    def appProvider2 = Stub(ApplicationProvider) {
      getApplication("app") >> app2
    }

    clusterController.applicationProviders = [appProvider1, appProvider2]

    when:
    def result = clusterController.list("app")

    then:
    result == [test: ["foo", "bar"] as Set, prod: ["baz"] as Set]
  }

  void "should throw exception when looking for specific cluster that doesnt exist"() {
    setup:
    def clusterProvider1 = Mock(ClusterProvider)
    clusterController.clusterProviders = [clusterProvider1]

    when:
    clusterController.getForAccountAndNameAndType("app", "test", "cluster", "aws")

    then:
    thrown ClusterController.ClusterNotFoundException
  }

  void "should return specific named serverGroup"() {
    setup:
    def clusterProvider1 = Mock(ClusterProvider)
    clusterController.clusterProviders = [clusterProvider1]
    def serverGroup = Mock(ServerGroup)
    serverGroup.getName() >> "serverGroupName"

    when:
    def result = clusterController.getServerGroup("app", "account", "clusterName", "type", "serverGroupName", null, null)

    then:
    1 * clusterProvider1.getCluster(_, _, _) >> {
      def cluster = Mock(Cluster)
      cluster.getType() >> "type"
      cluster.getServerGroups() >> [serverGroup]
      cluster
    }
    result == [serverGroup] as Set
  }

  void "should throw exception when no clusters are found for an account"() {
    setup:
    def clusterProvider1 = Mock(ClusterProvider)
    clusterController.clusterProviders = [clusterProvider1]

    when:
    clusterController.getForAccount("app", "account")

    then:
    1 * clusterProvider1.getClusters(_, _) >> null
    thrown ClusterController.AccountClustersNotFoundException
  }

  void "should throw exception when a requested cluster is not found within an account"() {
    setup:
    def clusterProvider1 = Mock(ClusterProvider)
    clusterController.clusterProviders = [clusterProvider1]

    when:
    clusterController.getForAccountAndName("app", "account", "name")

    then:
    1 * clusterProvider1.getCluster(*_) >> null
    thrown ClusterController.ClusterNotFoundException
  }
}
