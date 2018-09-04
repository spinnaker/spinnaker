/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import retrofit.RetrofitError
import spock.lang.Shared
import spock.lang.Specification

import com.netflix.spinnaker.clouddriver.model.ServerGroup.InstanceCounts as InstanceCounts

class ProjectControllerSpec extends Specification {

  @Shared
  ProjectController projectController

  @Shared
  Front50Service front50Service

  @Shared
  ClusterProvider clusterProvider

  @Shared
  String projectName

  @Shared
  Map projectConfig

  def setup() {
    projectController = new ProjectController(requestQueue: RequestQueue.noop())

    front50Service = Mock(Front50Service)
    projectController.front50Service = front50Service

    clusterProvider = Mock(ClusterProvider)
    projectController.clusterProviders = [clusterProvider]

    projectName = "Spinnaker"

    projectConfig = [
        config: [
            applications: ["orca", "deck"],
            clusters    : []
        ]
    ]
  }

  void "throws ProjectNotFoundException when project config read fails"() {
    setup:
    projectName = "Spinnakers"

    when:
    projectController.getClusters(projectName)

    then:
    1 * front50Service.getProject(projectName) >> { throw new RetrofitError("a", null, null, null, null, null, null) }
    thrown NotFoundException
  }

  void "returns an empty list without trying to retrieve applications when no clusters are configured"() {
    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters == []
    1 * front50Service.getProject(projectName) >> projectConfig
    0 * _
  }

  void "builds the very specific model we probably want for the project dashboard"() {
    projectConfig.config.clusters = [
        [account: "prod", stack: "main"]
    ]

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].account == "prod"
    clusters[0].stack == "main"
    clusters[0].detail == null

    clusters[0].applications[0].application == "orca"
    clusters[0].applications[0].lastPush == 2L
    clusters[0].applications[0].clusters[0].region == "us-east-1"
    clusters[0].applications[0].clusters[0].lastPush == 2L
    clusters[0].applications[0].clusters[0].instanceCounts.total == 1
    clusters[0].applications[0].clusters[0].instanceCounts.up == 1

    clusters[0].applications[1].application == "deck"
    clusters[0].applications[1].lastPush == 1L
    clusters[0].applications[1].clusters[0].region == "us-west-1"
    clusters[0].applications[1].clusters[0].lastPush == 1L
    clusters[0].applications[1].clusters[0].instanceCounts.total == 2
    clusters[0].applications[1].clusters[0].instanceCounts.down == 1
    clusters[0].applications[1].clusters[0].instanceCounts.up == 1

    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [new TestCluster(
            name: "orca-main",
            accountName: "prod",
            serverGroups: [
                makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 2L, new InstanceCounts(total: 1, up: 1))
            ]
        )]
    ]
    1 * clusterProvider.getClusterDetails("deck") >> [
        prod: [new TestCluster(
            name: "deck-main",
            accountName: "prod",
            serverGroups: [
                makeServerGroup("prod", "deck-main-v001", "us-west-1", 31, 1L, new InstanceCounts(total: 2, up: 1, down: 1))
            ]
        )]
    ]
  }

  void "includes all applications if none specified for a cluster"() {
    given:
    projectConfig.config.clusters = [
        [account: "prod", stack: "main"]
    ]

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca", "deck"]
    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [new TestCluster(
            name: "orca-main",
            accountName: "prod",
            serverGroups: [
                makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1))
            ]
        )]
    ]
    1 * clusterProvider.getClusterDetails("deck") >> [
        prod: [new TestCluster(
            name: "deck-main",
            accountName: "prod",
            serverGroups: [
                makeServerGroup("prod", "deck-main-v001", "us-west-1", 31, 1L, new InstanceCounts(total: 2, up: 1, down: 1))
            ]
        )]
    ]
  }

  void "only returns specified applications if declared in cluster config"() {
    projectConfig.config.clusters = [
        [account: "prod", stack: "main", applications: ["deck"]]
    ]

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["deck"]
    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [new TestCluster(
            name: "orca-main",
            accountName: "prod",
            serverGroups: [
                makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1))
            ]
        )]
    ]
    1 * clusterProvider.getClusterDetails("deck") >> [
        prod: [new TestCluster(
            name: "deck-main",
            accountName: "prod",
            serverGroups: [
                makeServerGroup("prod", "deck-main-v001", "us-west-1", 31, 1L, new InstanceCounts(total: 2, up: 1, down: 1))
            ]
        )]
    ]
  }

  void "includes all clusters on stack wildcard"() {
    projectConfig.config.clusters = [
        [account: "prod", stack: "*", applications: ["orca"]]
    ]

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 5L
    clusters[0].applications[0].clusters.size() == 2
    clusters[0].instanceCounts.total == 2
    clusters[0].instanceCounts.up == 2
    clusters[0].instanceCounts.starting == 0

    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [
            new TestCluster(
              name: "orca-main",
              accountName: "prod",
              serverGroups: [
                  makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1))
              ]),
            new TestCluster(
                name: "orca-test",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-test-v001", "us-west-1", 3, 5L, new InstanceCounts(total: 1, up: 1))
                ]),
            new TestCluster(
                name: "orca--foo",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca--foo-v001", "us-west-1", 3, 3L, new InstanceCounts(total: 1, starting: 1))
                ]),
        ]
    ]
  }

  void "includes all clusters on detail wildcard"() {
    projectConfig.config.clusters = [
        [account: "prod", detail: "*", applications: ["orca"]]
    ]

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 5L
    clusters[0].applications[0].clusters.size() == 2
    clusters[0].instanceCounts.total == 2
    clusters[0].instanceCounts.up == 2
    clusters[0].instanceCounts.starting == 0

    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [
            new TestCluster(
                name: "orca--foo",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca--foo-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1))
                ]),
            new TestCluster(
                name: "orca--bar",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca--bar-v001", "us-west-1", 3, 5L, new InstanceCounts(total: 1, up: 1))
                ]),
            new TestCluster(
                name: "orca-foo",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-foo-v001", "us-west-1", 3, 3L, new InstanceCounts(total: 1, starting: 1))
                ]),
        ]
    ]
  }

  void "excludes disabled server groups"() {
    projectConfig.config.clusters = [
        [account: "prod", stack: "main", applications: ["orca"]]
    ]

    TestServerGroup disabledServerGroup = makeServerGroup("prod", "orca-main-v003", "us-east-1", 5, 5L, new InstanceCounts(total: 1, up: 1))
    disabledServerGroup.disabled = true

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 4L
    clusters[0].applications[0].clusters.size() == 1
    clusters[0].instanceCounts.total == 2
    clusters[0].instanceCounts.up == 2

    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [
            new TestCluster(
                name: "orca-main",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1)),
                    makeServerGroup("prod", "orca-main-v002", "us-east-1", 4, 4L, new InstanceCounts(total: 1, up: 1)),
                    disabledServerGroup
                ]),
        ]
    ]
  }

  void "includes exactly matched clusters"() {
    projectConfig.config.clusters = [
        [account: "prod", stack: "main", detail: "foo", applications: ["orca"]]
    ]

    when:
    def clusters = projectController.getClusters(projectName)

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 1L
    clusters[0].applications[0].clusters.size() == 1
    clusters[0].instanceCounts.total == 1
    clusters[0].instanceCounts.up == 1

    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [
            new TestCluster(
                name: "orca-main-foo",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-main-foo-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1)),
                ]),
            new TestCluster(
                name: "orca-main-bar",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-main-bar-v002", "us-east-1", 4, 5L, new InstanceCounts(total: 1, up: 1)),
                ]),
            new TestCluster(
                name: "orca-main",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-main-v002", "us-east-1", 4, 6L, new InstanceCounts(total: 1, up: 1)),
                ]),
            new TestCluster(
                name: "orca--foo",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca--foo-v002", "us-east-1", 4, 7L, new InstanceCounts(total: 1, up: 1)),
                ]),
        ]
    ]
  }

  void "includes all builds per region with latest deployment date, ignoring disabled server groups"() {
    given:
    projectConfig.config.clusters = [
        [account: "prod", stack: "main", detail: "foo", applications: ["orca"]]
    ]
    def disabledServerGroup = makeServerGroup("prod", "orca-main-foo-v005", "us-west-1", 6, 7L, new InstanceCounts(total: 1, up: 1))
    disabledServerGroup.disabled = true

    when:
    def clusters = projectController.getClusters(projectName)
    def eastCluster = clusters[0].applications[0].clusters.find { it.region == "us-east-1"}
    def westCluster = clusters[0].applications[0].clusters.find { it.region == "us-west-1"}

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 6L
    clusters[0].applications[0].clusters.size() == 2

    eastCluster.lastPush == 1L
    eastCluster.builds.size() == 1
    eastCluster.builds[0].buildNumber == "3"
    eastCluster.builds[0].deployed == 1L

    westCluster.lastPush == 6L
    westCluster.builds.size() == 2
    westCluster.builds[0].buildNumber == "4"
    westCluster.builds[0].deployed == 2L
    westCluster.builds[1].buildNumber == "5"
    westCluster.builds[1].deployed == 6L

    clusters[0].instanceCounts.total == 4
    clusters[0].instanceCounts.up == 4

    eastCluster.instanceCounts.total == 1
    eastCluster.instanceCounts.up == 1

    westCluster.instanceCounts.total == 3
    westCluster.instanceCounts.up == 3

    1 * front50Service.getProject(projectName) >> projectConfig
    1 * clusterProvider.getClusterDetails("orca") >> [
        prod: [
            new TestCluster(
                name: "orca-main-foo",
                accountName: "prod",
                serverGroups: [
                    makeServerGroup("prod", "orca-main-foo-v001", "us-east-1", 3, 1L, new InstanceCounts(total: 1, up: 1)),
                    makeServerGroup("prod", "orca-main-foo-v003", "us-west-1", 4, 2L, new InstanceCounts(total: 1, up: 1)),
                    makeServerGroup("prod", "orca-main-foo-v004", "us-west-1", 5, 3L, new InstanceCounts(total: 1, up: 1)),
                    makeServerGroup("prod", "orca-main-foo-v005", "us-west-1", 5, 6L, new InstanceCounts(total: 1, up: 1)),
                    disabledServerGroup
                ])
        ]
    ]
  }


  TestServerGroup makeServerGroup(String account, String name, String region, Integer buildNumber, Long createdTime, InstanceCounts instanceCounts) {
    new TestServerGroup(
        name: name,
        accountName: account,
        region: region,
        imageSummary: new TestImageSummary(buildInfo: [jenkins: [name: 'job', host: 'host', number: buildNumber.toString()]]),
        createdTime: createdTime,
        instanceCounts: instanceCounts,
    )
  }

  static class TestImageSummary implements ServerGroup.ImageSummary {
    String getServerGroupName() { null }
    String getImageId() { null }
    String getImageName() { null }

    Map<String, Object> getImage() { null }

    Map<String, Object> buildInfo
  }

  static class TestServerGroup implements ServerGroup {
    String name
    String accountName
    ServerGroup.ImageSummary imageSummary
    ServerGroup.ImagesSummary imagesSummary
    Long createdTime
    InstanceCounts instanceCounts
    String type = "test"
    String cloudProvider = "test"
    String region
    Boolean disabled
    Set<String> instances = []
    Set<String> loadBalancers
    Set<String> securityGroups
    Map<String, Object> launchConfig
    ServerGroup.Capacity capacity
    Set<String> zones

    Boolean isDisabled() { disabled }
  }

  static class TestCluster implements Cluster {
    String name
    String type = "test"
    String accountName
    Set<ServerGroup> serverGroups
    Set<LoadBalancer> loadBalancers
  }

}
