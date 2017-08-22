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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ClusterControllerSpec extends Specification {

  @Shared
  ClusterController clusterController

  def setup() {
    clusterController = new ClusterController(requestQueue: RequestQueue.noop())
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
      def result = clusterController.listByAccount("app")

    then:
      result == [test: ["foo", "bar"] as Set, prod: ["baz"] as Set]
  }

  void "should throw exception when looking for specific cluster that doesnt exist"() {
    setup:
      def clusterProvider1 = Mock(ClusterProvider)
      clusterController.clusterProviders = [clusterProvider1]

    when:
      clusterController.getForAccountAndNameAndType("app", "test", "cluster", "aws", true)

    then:
      thrown NotFoundException
  }

  void "should return specific named serverGroup"() {
    setup:
      def serverGroupController = Mock(ServerGroupController)

      def clusterProvider1 = Mock(ClusterProvider)
      clusterController.clusterProviders = [clusterProvider1]
      clusterController.serverGroupController = serverGroupController

      def serverGroup = new AmazonServerGroup(name: "clusterName-v001", region: "us-west-2")

    when:
      def result = clusterController.getServerGroup("app", "account", "clusterName", "type", "clusterName-v001", null)

    then:
      1 * clusterProvider1.getCluster("app", "account", "clusterName", false) >> {
        def cluster = Mock(Cluster)
        cluster.getType() >> "type"
        cluster.getServerGroups() >> [serverGroup]
        cluster
      }
      1 * serverGroupController.getServerGroup("app", "account", "us-west-2", "clusterName-v001") >> serverGroup
      0 * _
      result == [serverGroup]
  }

  void "should delegate to ServerGroupController when region is provided"() {
    given:
    def serverGroupController = Mock(ServerGroupController)
    clusterController.serverGroupController = serverGroupController

    and:
    def serverGroup = new AmazonServerGroup(type: "aws")

    when:
    def result = clusterController.getServerGroup("app", "test", "mycluster", "aws", "mycluster-v001", "us-west-2")

    then:
    1 * serverGroupController.getServerGroup("app", "test", "us-west-2", "mycluster-v001") >> { return serverGroup }
    result == serverGroup

    when:
    clusterController.getServerGroup("app", "test", "mycluster", "google", "mycluster-v001", "us-west-2")

    then:
    1 * serverGroupController.getServerGroup("app", "test", "us-west-2", "mycluster-v001") >> { return serverGroup }
    thrown(NotFoundException)
  }

  void "should throw exception when no clusters are found for an account"() {
    setup:
      def clusterProvider1 = Mock(ClusterProvider)
      clusterController.clusterProviders = [clusterProvider1]

    when:
      clusterController.getForAccount("app", "account")

    then:
      1 * clusterProvider1.getClusters(_, _) >> null
      thrown NotFoundException
  }

  void "should throw exception when a requested cluster is not found within an account"() {
    setup:
      def clusterProvider1 = Mock(ClusterProvider)
      clusterController.clusterProviders = [clusterProvider1]

    when:
      clusterController.getForAccountAndName("app", "account", "name", true)

    then:
      1 * clusterProvider1.getCluster(*_) >> null
      thrown NotFoundException
  }

  @Unroll
  void "should return the server group for the '#location:#target' target"() {
    given:
      def serverGroupController = Mock(ServerGroupController)

      def clusterProvider1 = Mock(ClusterProvider)
      clusterController.clusterProviders = [clusterProvider1]
      clusterController.serverGroupController = serverGroupController

      def serverGroup1 = Mock(ServerGroup)
      with(serverGroup1) {
        getName() >> "serverGroup-v1"
        getRegion() >> "north"
        getZones() >> ["abc"]
        getCreatedTime() >> 1111
        isDisabled() >> false
        getInstances() >> [Mock(Instance)]
      }
      def serverGroup2 = Mock(ServerGroup)
      with(serverGroup2) {
        getName() >> "serverGroup-v2"
        getRegion() >> "north"
        getZones() >> []
        getCreatedTime() >> 2222
        isDisabled() >> false
        getInstances() >> [Mock(Instance)]
      }
      def serverGroup3 = Mock(ServerGroup)
      with(serverGroup3) {
        getName() >> "serverGroup-v2"
        getRegion() >> "south"
        getZones() >> []
        getCreatedTime() >> 2222
        isDisabled() >> true
        getInstances() >> [Mock(Instance)]
      }
      def serverGroup4 = Mock(ServerGroup)
      with(serverGroup4) {
        getName() >> "serverGroup-v3"
        getRegion() >> "south"
        getZones() >> ["def"]
        getCreatedTime() >> 3333
        isDisabled() >> false
        getInstances() >> [Mock(Instance), Mock(Instance)]
      }

      clusterProvider1.getCluster(*_) >> {
        def cluster = Mock(Cluster)
        with(cluster) {
          getType() >> "cloudProvider"
          getServerGroups() >> [serverGroup1, serverGroup2, serverGroup3, serverGroup4]
        }
        cluster
      }
      serverGroupController.getServerGroup(_, _, "north", "serverGroup-v1") >> { return serverGroup1 }
      serverGroupController.getServerGroup(_, _, "south", "serverGroup-v2") >> { return serverGroup2 }
      serverGroupController.getServerGroup(_, _, "north", "serverGroup-v2") >> { return serverGroup3 }
      serverGroupController.getServerGroup(_, _, "south", "serverGroup-v3") >> { return serverGroup4 }

    when:
      def result = clusterController.getTargetServerGroup(
          "app", "account", "clusterName", "cloudProvider",
          location, target, onlyEnabled.toString(), validateOldest.toString()
      )

    then:
      result
      result.getName() == expectedName

    when:
      clusterController.getTargetServerGroup(
          "app", "account", "clusterName", "cloudProvider",
          "north", "fail", false.toString() /* onlyEnabled */, true.toString() /* validateOldest */
      )

    then:
      thrown NotFoundException

    when:
      clusterController.getTargetServerGroup(
          "app", "account", "clusterName", "cloudProvider",
          "south", "oldest", true.toString() /* onlyEnabled */, true.toString() /* validateOldest */
      )

    then:
      thrown NotFoundException

    where:
      location | target                | onlyEnabled | validateOldest || expectedName
      "north"  | "newest"              | false       | true           || "serverGroup-v2"
      "north"  | "current"             | false       | true           || "serverGroup-v2"
      "north"  | "current_asg"         | false       | true           || "serverGroup-v2"
      "north"  | "current_asg_dynamic" | false       | true           || "serverGroup-v2"
      "north"  | "previous"            | false       | true           || "serverGroup-v1"
      "north"  | "oldest"              | false       | true           || "serverGroup-v1"
      "north"  | "largest"             | false       | true           || "serverGroup-v2"

      "south"  | "current"             | false       | true           || "serverGroup-v3"
      "south"  | "previous"            | false       | true           || "serverGroup-v2"
      "south"  | "oldest"              | false       | true           || "serverGroup-v2"
      "south"  | "oldest"              | true        | false          || "serverGroup-v3"
      "south"  | "largest"             | false       | true           || "serverGroup-v3"
      "south"  | "fail"                | true        | true           || "serverGroup-v3"

      "abc"    | "current"             | false       | true           || "serverGroup-v1"
  }

  def "should get ImageSummary from serverGroup"() {
    given:
      def serverGroupController = Mock(ServerGroupController)

      def clusterProvider1 = Mock(ClusterProvider)
      clusterController.clusterProviders = [clusterProvider1]
      clusterController.serverGroupController = serverGroupController

      def mockImageSummary = Mock(ServerGroup.ImageSummary)
      def serverGroup1 = Mock(ServerGroup)
      with(serverGroup1) {
        getName() >> "serverGroup-v1"
        getRegion() >> "north"
        getZones() >> ["abc"]
        getCreatedTime() >> 1111
        isDisabled() >> false
        getInstances() >> [Mock(Instance)]
        getImageSummary() >> mockImageSummary
      }

      clusterProvider1.getCluster(*_) >> {
        def cluster = Mock(Cluster)
        with(cluster) {
          getType() >> "cloudProvider"
          getServerGroups() >> [serverGroup1]
        }
        cluster
      }
      serverGroupController.getServerGroup(*_)  >> { return serverGroup1 }

    when:
      def result = clusterController.getServerGroupSummary(
          "app", "account", "clusterName", "cloudProvider",
          "north", "largest", "image", null /* onlyEnabled */)

    then:
      result == mockImageSummary

    when:
      clusterController.getServerGroupSummary(
          "app", "account", "clusterName", "cloudProvider",
          "north", "largest", "doesntExist", null /* onlyEnabled */)

    then:
      thrown NotFoundException
  }
}
