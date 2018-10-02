/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Provider

import static com.netflix.spinnaker.clouddriver.core.ProjectClustersService.ClusterModel

class ProjectClustersServiceSpec extends Specification {

  @Shared
  ProjectClustersService subject

  @Shared
  Front50Service front50Service

  @Shared
  ClusterProvider clusterProvider

  @Shared
  Map projectConfig = [
    name  : "Spinnaker",
    config: [
      applications: ["orca", "deck"],
      clusters    : []
    ]
  ]

  @Shared
  List<String> allowList = ["Spinnaker"]

  def setup() {
    front50Service = Mock()
    clusterProvider = Mock()

    subject = new ProjectClustersService(
      front50Service,
      new ObjectMapper(),
      new Provider<List<ClusterProvider>>() {
        @Override
        List<ClusterProvider> get() {
          return [clusterProvider]
        }
      }
    )
  }

  void "returns an empty list without trying to retrieve applications when no clusters are configured"() {
    when:
    def result = subject.getProjectClusters(allowList)

    then:
    result["Spinnaker"].isEmpty()
    1 * front50Service.getProject(_) >> { projectConfig }
    0 * _
  }

  void "builds the very specific model we probably want for the project dashboard"() {
    projectConfig.config.clusters = [
      [account: "prod", stack: "main"]
    ]

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]

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

    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [new TestCluster(
        name: "orca-main",
        accountName: "prod",
        serverGroups: []
      )] as Set
    ]
    1 * clusterProvider.getCluster("orca", "prod", "orca-main") >> new TestCluster(
      name: "orca-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 2L, new ServerGroup.InstanceCounts(total: 1, up: 1))
      ]
    )

    1 * clusterProvider.getClusterSummaries("deck") >> [
      prod: [new TestCluster(
        name: "deck-main",
        accountName: "prod",
        serverGroups: []
      )] as Set
    ]
    1 * clusterProvider.getCluster("deck", "prod", "deck-main") >> new TestCluster(
      name: "deck-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "deck-main-v001", "us-west-1", 31, 1L, new ServerGroup.InstanceCounts(total: 2, up: 1, down: 1))
      ]
    )

    0 * clusterProvider._
  }

  void "includes all applications if none specified for a cluster"() {
    given:
    projectConfig.config.clusters = [
      [account: "prod", stack: "main"]
    ]

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca", "deck"]
    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [new TestCluster(
        name: "orca-main",
        accountName: "prod",
        serverGroups: []
      )] as Set
    ]
    1 * clusterProvider.getCluster("orca", "prod", "orca-main") >> new TestCluster(
      name: "orca-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new ServerGroup.InstanceCounts(total: 1, up: 1))
      ]
    )

    1 * clusterProvider.getClusterSummaries("deck") >> [
      prod: [new TestCluster(
        name: "deck-main",
        accountName: "prod",
        serverGroups: []
      )] as Set
    ]
    1 * clusterProvider.getCluster("deck", "prod", "deck-main") >> new TestCluster(
      name: "deck-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "deck-main-v001", "us-west-1", 31, 1L, new ServerGroup.InstanceCounts(total: 2, up: 1, down: 1))
      ]
    )

    0 * clusterProvider._
  }


  void "only returns specified applications if declared in cluster config"() {
    projectConfig.config.clusters = [
      [account: "prod", stack: "main", applications: ["deck"]]
    ]

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["deck"]
    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [new TestCluster(
        name: "orca-main",
        accountName: "prod",
        serverGroups: []
      )] as Set
    ]
    1 * clusterProvider.getCluster("orca", "prod", "orca-main") >> new TestCluster(
      name: "orca-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new ServerGroup.InstanceCounts(total: 1, up: 1))
      ]
    )

    1 * clusterProvider.getClusterSummaries("deck") >> [
      prod: [new TestCluster(
        name: "deck-main",
        accountName: "prod",
        serverGroups: []
      )] as Set
    ]
    1 * clusterProvider.getCluster("deck", "prod", "deck-main") >> new TestCluster(
      name: "deck-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "deck-main-v001", "us-west-1", 31, 1L, new ServerGroup.InstanceCounts(total: 2, up: 1, down: 1))
      ]
    )

    0 * clusterProvider._
  }

  void "includes all clusters on stack wildcard"() {
    projectConfig.config.clusters = [
      [account: "prod", stack: "*", applications: ["orca"]]
    ]

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 5L
    clusters[0].applications[0].clusters.size() == 2
    clusters[0].instanceCounts.total == 2
    clusters[0].instanceCounts.up == 2
    clusters[0].instanceCounts.starting == 0

    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("deck") >> [:]
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [
        new TestCluster(
          name: "orca-main",
          accountName: "prod",
          serverGroups: []
        ),
        new TestCluster(
          name: "orca-test",
          accountName: "prod",
          serverGroups: []
        ),
        new TestCluster(
          name: "orca--foo",
          accountName: "prod",
          serverGroups: []
        ),
      ] as Set
    ]

    1 * clusterProvider.getCluster("orca", "prod", "orca-main") >> new TestCluster(
      name: "orca-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new ServerGroup.InstanceCounts(total: 1, up: 1))
      ]
    )
    1 * clusterProvider.getCluster("orca", "prod", "orca-test") >> new TestCluster(
      name: "orca-test",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-test-v001", "us-west-1", 3, 5L, new ServerGroup.InstanceCounts(total: 1, up: 1))
      ]
    )
    0 * clusterProvider._
  }

  void "excludes disabled server groups"() {
    projectConfig.config.clusters = [
      [account: "prod", stack: "main", applications: ["orca"]]
    ]

    TestServerGroup disabledServerGroup = makeServerGroup("prod", "orca-main-v003", "us-east-1", 5, 5L, new ServerGroup.InstanceCounts(total: 1, up: 1))
    disabledServerGroup.disabled = true

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 4L
    clusters[0].applications[0].clusters.size() == 1
    clusters[0].instanceCounts.total == 2
    clusters[0].instanceCounts.up == 2

    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("deck") >> [:]
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [
        new TestCluster(
          name: "orca-main",
          accountName: "prod",
          serverGroups: []),
      ] as Set
    ]
    1 * clusterProvider.getCluster("orca", "prod", "orca-main") >> new TestCluster(
      name: "orca-main",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-v001", "us-east-1", 3, 1L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
        makeServerGroup("prod", "orca-main-v002", "us-east-1", 4, 4L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
        disabledServerGroup
      ])

    0 * clusterProvider._
  }

  void "includes exactly matched clusters"() {
    projectConfig.config.clusters = [
      [account: "prod", stack: "main", detail: "foo", applications: ["orca"]]
    ]

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]

    then:
    clusters.size() == 1
    clusters[0].applications.application == ["orca"]
    clusters[0].applications[0].lastPush == 1L
    clusters[0].applications[0].clusters.size() == 1
    clusters[0].instanceCounts.total == 1
    clusters[0].instanceCounts.up == 1

    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("deck") >> [:]
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [
        new TestCluster(
          name: "orca-main-foo",
          accountName: "prod",
          serverGroups: []),
        new TestCluster(
          name: "orca-main-bar",
          accountName: "prod",
          serverGroups: []),
        new TestCluster(
          name: "orca-main",
          accountName: "prod",
          serverGroups: []),
        new TestCluster(
          name: "orca--foo",
          accountName: "prod",
          serverGroups: []),
      ] as Set
    ]
    1 * clusterProvider.getCluster("orca", "prod", "orca-main-foo") >> new TestCluster(
      name: "orca-main-foo",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-foo-v001", "us-east-1", 3, 1L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
      ])
    0 * clusterProvider._
  }

  void "includes all builds per region with latest deployment date, ignoring disabled server groups"() {
    given:
    projectConfig.config.clusters = [
      [account: "prod", stack: "main", detail: "foo", applications: ["orca"]]
    ]
    def disabledServerGroup = makeServerGroup("prod", "orca-main-foo-v005", "us-west-1", 6, 7L, new ServerGroup.InstanceCounts(total: 1, up: 1))
    disabledServerGroup.disabled = true

    when:
    def result = subject.getProjectClusters(allowList)
    def clusters = result["Spinnaker"]
    def eastCluster = clusters[0].applications[0].clusters.find { it.region == "us-east-1" }
    def westCluster = clusters[0].applications[0].clusters.find { it.region == "us-west-1" }

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

    1 * front50Service.getProject(_) >> { projectConfig }
    1 * clusterProvider.getClusterSummaries("orca") >> [
      prod: [
        new TestCluster(
          name: "orca-main-foo",
          accountName: "prod",
          serverGroups: [])
      ] as Set
    ]
    1 * clusterProvider.getCluster("orca", "prod", "orca-main-foo") >> new TestCluster(
      name: "orca-main-foo",
      accountName: "prod",
      serverGroups: [
        makeServerGroup("prod", "orca-main-foo-v001", "us-east-1", 3, 1L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
        makeServerGroup("prod", "orca-main-foo-v003", "us-west-1", 4, 2L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
        makeServerGroup("prod", "orca-main-foo-v004", "us-west-1", 5, 3L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
        makeServerGroup("prod", "orca-main-foo-v005", "us-west-1", 5, 6L, new ServerGroup.InstanceCounts(total: 1, up: 1)),
        disabledServerGroup
      ])
  }

  private static List<ClusterModel> cachedClusters(Map<String, List<ClusterModel>> result, String projectName) {
    return result[projectName]
  }

  TestServerGroup makeServerGroup(String account,
                                  String name,
                                  String region,
                                  Integer buildNumber,
                                  Long createdTime,
                                  ServerGroup.InstanceCounts instanceCounts) {
    def imageSummary = new TestImageSummary(buildInfo: [jenkins: [name: 'job', host: 'host', number: buildNumber.toString()]])
    new TestServerGroup(
      name: name,
      accountName: account,
      region: region,
      imageSummary: imageSummary,
      imagesSummary: new TestImagesSummary(summaries: [imageSummary]),
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

  static class TestImagesSummary implements ServerGroup.ImagesSummary {
    List<? extends ServerGroup.ImageSummary> summaries = []
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
    boolean disabled
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
