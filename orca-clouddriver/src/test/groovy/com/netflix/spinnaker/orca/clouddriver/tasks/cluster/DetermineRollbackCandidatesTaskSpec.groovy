/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static DetermineRollbackCandidatesTask.determineTargetHealthyRollbackPercentage
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class DetermineRollbackCandidatesTaskSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def featuresService = Mock(FeaturesService)
  CloudDriverService cloudDriverService = Mock()

  @Subject
  def task = new DetermineRollbackCandidatesTask(
      objectMapper,
      new RetrySupport(),
      cloudDriverService,
      featuresService
  )

  def stage = stage {
    context = [
        credentials: "test",
        cloudProvider: "aws",
        regions: ["us-west-2"],
        moniker: [
            app: "app",
            cluster: "app-stack-details"
        ]
    ]
  }

  def "should EXPLICIT-ly roll back to original ASG when original cluster is in context"() {
    given: "the name of the original server group is present in the context"
    stage.context["originalServerGroup"] = "servergroup-v001"

    and: "entity tags contain metadata with the image id of the original server group"
    featuresService.areEntityTagsAvailable() >> { return true }
    cloudDriverService.getEntityTags(*_) >> {
      return [buildSpinnakerMetadata("my_image-0", "ami-xxxxx0", "5")]
    }

    and: "there are at least two server groups"
    cloudDriverService.getCluster("app", "test", "app-stack-details", "aws") >> [
        serverGroups: [
            buildServerGroup("servergroup-v001", "us-west-2", 100, true, [:], [:], 5),
            buildServerGroup("servergroup-v002", "us-west-2", 150, false, [:], [:], 5)
        ]
    ]

    when: "the task is executed"
    def result = task.execute(stage)

    then: "it uses the EXPLICIT strategy to roll back to the original server group"
    result.context == [
        imagesToRestore: [
            [region: "us-west-2", image: "my_image-0", buildNumber: "5", rollbackMethod: "EXPLICIT"]
        ]
    ]

    result.outputs == [
        rollbackTypes: [
            "us-west-2": "EXPLICIT"
        ],
        rollbackContexts: [
            "us-west-2": [
                rollbackServerGroupName: "servergroup-v002",
                restoreServerGroupName: "servergroup-v001",
                targetHealthyRollbackPercentage: 100
            ]
        ]
    ]
  }

  @Unroll
  def "should build EXPLICIT rollback context when there are _only_ previous server groups"() {
    given:
    stage.context.putAll(additionalStageContext)

    when: "there are previous server groups but no entity tags"
    def result = task.execute(stage)

    then:
    (shouldFetchServerGroup ? 1 : 0) * cloudDriverService.getServerGroupTyped("test", "us-west-2", "servergroup-v002") >> new ServerGroup(
        moniker: new Moniker(
            app: "app",
            cluster: "app-stack-details"
        ))

    1 * cloudDriverService.getCluster("app", "test", "app-stack-details", "aws") >> [
        serverGroups: [
            buildServerGroup("servergroup-v000", "us-west-2", 50, false, [name: "my_image-0"], [:], 5),
            // disabled server groups should be skipped when evaluating rollback candidates, but only on automatic rollbacks after a failed deployment
            buildServerGroup("servergroup-v001", "us-west-2", 100, true, [name: "my_image-1"], [:], 5),
            buildServerGroup("servergroup-v002", "us-west-2", 150, false, [name: "my_image-2"], [:], 5)
        ]
    ]
    1 * featuresService.areEntityTagsAvailable() >> areEntityTagsEnabled
    (shouldFetchEntityTags ? 1 : 0) * cloudDriverService.getEntityTags(*_) >> { return [] }

    result.context == [
        imagesToRestore: [
            [region: "us-west-2", image: expectedImage, rollbackMethod: "EXPLICIT"]
        ]
    ]
    result.outputs == [
        rollbackTypes: [
            "us-west-2": "EXPLICIT"
        ],
        rollbackContexts: [
            "us-west-2": [
                rollbackServerGroupName: "servergroup-v002",
                restoreServerGroupName: expectedCandidate,
                targetHealthyRollbackPercentage: 100
            ]
        ]
    ]

    where:
    additionalStageContext                                 | areEntityTagsEnabled || shouldFetchServerGroup || shouldFetchEntityTags || expectedCandidate  || expectedImage
    [:]                                                    | true                 || false                  || true                  || "servergroup-v001" || "my_image-1"
    [moniker: null, serverGroup: "servergroup-v002"]       | false                || true                   || false                 || "servergroup-v001" || "my_image-1"
    buildAdditionalStageContext("servergroup-v002", false) | false                || true                   || false                 || "servergroup-v001" || "my_image-1"
    buildAdditionalStageContext("servergroup-v002", true)  | false                || true                   || false                 || "servergroup-v000" || "my_image-0"
  }

  private static def buildAdditionalStageContext(String serverGroup, boolean onlyEnabledServerGroups) {
    [moniker: null, serverGroup: serverGroup, additionalRollbackContext: [onlyEnabledServerGroups: onlyEnabledServerGroups]]
  }

  def "should build EXPLICIT rollback context when entity tags contain previousServerGroup info and there's a corresponding active server group"() {
    given: "entity tags contain metadata with the image id of the previous server group"
    featuresService.areEntityTagsAvailable() >> { return true }
    cloudDriverService.getEntityTags(*_) >> {
      return [buildSpinnakerMetadata("my_image-0", "ami-xxxxx0", "5")]
    }

    and: "there's an enabled server group with the same image id"
    cloudDriverService.getCluster("app", "test", "app-stack-details", "aws") >> [
        serverGroups: [
            buildServerGroup("servergroup-v001", "us-west-2", 100, false, [name: "my_image-0", imageId: "ami-xxxxx0"], [:], 5),
            buildServerGroup("servergroup-v002", "us-west-2", 150, false, [name: "my_image-1", imageId: "ami-xxxxx1"], [:], 5)
        ]
    ]

    when: "the task executes"
    def result = task.execute(stage)

    then:
    result.context["imagesToRestore"]["rollbackMethod"] == ["EXPLICIT"]

    result.context == [
        imagesToRestore: [
            [region: "us-west-2", image: "my_image-0", buildNumber: "5", rollbackMethod: "EXPLICIT"]
        ]
    ]
    result.outputs == [
        rollbackTypes: [
            "us-west-2": "EXPLICIT"
        ],
        rollbackContexts: [
            "us-west-2": [
                rollbackServerGroupName: "servergroup-v002",
                restoreServerGroupName: "servergroup-v001",
                targetHealthyRollbackPercentage: 100
            ]
        ]
    ]
  }

  def "should build PREVIOUS_IMAGE rollback context when there are _only_ entity tags"() {
    when: "there are no previous server groups but there are entity tags"
    def result = task.execute(stage)

    then:
    1 * cloudDriverService.getCluster("app", _, "app-stack-details", _) >> [
        serverGroups: [
            buildServerGroup("servergroup-v002", "us-west-2", 50, false, null, [:], 80),
            buildServerGroup("servergroup-v001", "us-west-2", 100, false, [:], [:], 80),
        ]
    ]
    1 * featuresService.areEntityTagsAvailable() >> { return true }
    1 * cloudDriverService.getEntityTags(*_) >> {
      return [buildSpinnakerMetadata("my_image-0", "ami-xxxxx0", "5")]
    }

    result.context == [
        imagesToRestore: [
            [region: "us-west-2", image: "my_image-0", buildNumber: "5", rollbackMethod: "PREVIOUS_IMAGE"]
        ]
    ]
    result.outputs == [
        rollbackTypes: [
            "us-west-2": "PREVIOUS_IMAGE"
        ],
        rollbackContexts: [
            "us-west-2": [
                rollbackServerGroupName: "servergroup-v001",
                "imageId": "ami-xxxxx0",
                "imageName": "my_image-0",
                "targetHealthyRollbackPercentage": 95             // calculated based on `capacity.desired` of server group
            ]
        ]
    ]
  }

  @Unroll
  def "should calculate 'targetHealthyRollbackPercentage' when not explicitly provided"() {
    given:
    def capacity = ServerGroup.Capacity.builder().min(1).max(100).desired(desired).build()

    expect:
    determineTargetHealthyRollbackPercentage(capacity, override) == expectedTargetHealthyRollbackPercentage

    where:
    desired | override || expectedTargetHealthyRollbackPercentage
    3       | null     || 100
    50      | null     || 95
    10      | null      | 90
    10      | 100       | 100
  }

  private static Map buildServerGroup(String name,
                                      String region,
                                      Long createdTime,
                                      Boolean disabled,
                                      Map image,
                                      Map buildInfo,
                                      int desiredCapacity) {
    return [
        name: name,
        region: region,
        createdTime: createdTime,
        disabled: disabled,
        image: image,
        buildInfo: buildInfo,
        capacity: [
            min: 0,
            max: desiredCapacity * 2,
            desired: desiredCapacity
        ]
    ]
  }

  private static Map buildSpinnakerMetadata(String imageName, String imageId, String buildNumber) {
    return [
        tags: [
            [
                name: "spinnaker:metadata",
                value: [
                    previousServerGroup: [
                        imageName: imageName,
                        imageId: imageId,
                        buildInfo: [
                            jenkins: [
                                number: buildNumber
                            ]
                        ]
                    ]
                ]
            ]
        ]
    ]
  }
}
