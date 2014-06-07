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

import java.lang.Void as Should

class ClusterControllerSpec extends Specification {

  @Shared
  ClusterController clusterController

  def setup() {
    clusterController = new ClusterController()
  }

  Should "call all application providers to get and merge the cluster names"() {
    setup:
    def appProvider1 = Mock(ApplicationProvider)
    def appProvider2 = Mock(ApplicationProvider)
    def app1 = Mock(Application)
    app1.getName() >> "app"
    def app2 = Mock(Application)
    app2.getName() >> "app"
    clusterController.applicationProviders = [appProvider1, appProvider2]

    when:
    def result = clusterController.list("app")

    then:
    _ * app1.getClusterNames() >> ["test": ["foo", "bar"] as Set]
    _ * app2.getClusterNames() >> ["prod": ["baz"] as Set]
    1 * appProvider1.getApplication("app") >> app1
    1 * appProvider2.getApplication("app") >> app2
    result == [test: ["foo", "bar"] as Set, prod: ["baz"] as Set]
  }

  Should "throw exception when looking for specific cluster that doesnt exist"() {
    setup:
    def clusterProvider1 = Mock(ClusterProvider)
    clusterController.clusterProviders = [clusterProvider1]

    when:
    clusterController.getForAccountAndNameAndType("app", "test", "cluster", "aws")

    then:
    thrown ClusterController.ClusterNotFoundException
  }

  Should "return specific serverGroup"() {
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
    result.is(serverGroup)
  }
}
