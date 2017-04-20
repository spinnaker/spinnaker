/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ApplySourceServerGroupSnapshotTaskSpec extends Specification {
  def oortHelper = Mock(OortHelper)
  def executionRepository = Mock(ExecutionRepository)
  def stageNavigator = new StageNavigator(Stub(ApplicationContext))

  @Subject
  def task = new ApplySourceServerGroupCapacityTask(
    oortHelper: oortHelper,
    executionRepository: executionRepository,
    objectMapper: new ObjectMapper()
  )

  void "should support ancestor deploy stages w/ a custom strategy"() {
    given:
    def parentPipeline = new Pipeline()
    parentPipeline.stages << new Stage<>(parentPipeline, "", [
      strategy                         : "custom",
      sourceServerGroupCapacitySnapshot: [
        min    : 0,
        desired: 5,
        max    : 10
      ]
    ])
    parentPipeline.stages << new Stage<>(parentPipeline, "", [
      executionId: "execution-id"
    ])
    parentPipeline.stages[0].id = "stage-1"
    parentPipeline.stages[1].parentStageId = "stage-1"
    parentPipeline.stages[1].type = "pipeline"

    def childPipeline = new Pipeline()
    childPipeline.stages << new Stage<>(childPipeline, "", [
      type: "doSomething"
    ])
    childPipeline.stages << new Stage<>(childPipeline, "", [
      type: "createServerGroup"
    ])
    childPipeline.stages << new Stage<>(childPipeline, "", [
      type                  : "createServerGroup",
      "deploy.server.groups": [
        "us-west-1": ["asg-v001"]
      ]
    ])

    (parentPipeline.stages + childPipeline.stages).each {
      it.setStageNavigator(stageNavigator)
    }

    when:
    def ancestorDeployStage = ApplySourceServerGroupCapacityTask.getAncestorDeployStage(
      executionRepository, parentPipeline.stages[-1]
    )

    then:
    1 * executionRepository.retrievePipeline("execution-id") >> childPipeline

    // should match the first childPipeline of type 'createServerGroup' w/ 'deploy.server.groups'
    ancestorDeployStage == childPipeline.stages[2]
  }

  @Unroll
  void "should support ancestor deploy stages w/ a #strategy strategy"() {
    given:
    def stage = new Stage<>(new Pipeline(), "", [
      strategy                         : strategy,
      sourceServerGroupCapacitySnapshot: [
        min    : 0,
        desired: 5,
        max    : 10
      ]
    ])
    stage.setStageNavigator(stageNavigator)

    expect:
    ApplySourceServerGroupCapacityTask.getAncestorDeployStage(null, stage) == stage

    where:
    strategy   || _
    "redblack" || _
    null       || _
  }

  @Unroll
  void "should construct resizeServerGroup context with source `min` + target `desired` and `max` capacity"() {
    given:
    def sourceServerGroupCapacitySnapshot = [
      min    : originalMinCapacity,
      desired: 10,
      max    : 20
    ]

    task = new ApplySourceServerGroupCapacityTask() {
      @Override
      ApplySourceServerGroupCapacityTask.TargetServerGroupContext getTargetServerGroupContext(Stage stage) {
        return new ApplySourceServerGroupCapacityTask.TargetServerGroupContext(
          context: [
            credentials    : "test",
            region         : "us-east-1",
            asgName        : "application-stack-v001",
            serverGroupName: "application-stack-v001",
            cloudProvider  : "aws"
          ],
          sourceServerGroupCapacitySnapshot: sourceServerGroupCapacitySnapshot
        )
      }
    }
    task.oortHelper = oortHelper

    and:
    def targetServerGroup = new TargetServerGroup(
      name: "application-stack-v001",
      capacity: [
        min    : 5,
        desired: 5,
        max    : 10
      ]
    )

    when:
    def result = task.convert(null)

    then:
    1 * oortHelper.getTargetServerGroup(
      "test",
      "application-stack-v001",
      "us-east-1",
      "aws"
    ) >> Optional.of(targetServerGroup)

    result == [
      cloudProvider  : "aws",
      credentials    : "test",
      asgName        : "application-stack-v001",
      serverGroupName: "application-stack-v001",
      region         : "us-east-1",
      capacity       : [
        min    : expectedMinCapacity,
        desired: 5,
        max    : 10
      ]
    ]

    where:
    originalMinCapacity || expectedMinCapacity
    0                   || 0            // min(currentMin, snapshotMin) == 0
    10                  || 5            // min(currentMin, snapshotMin) == 5
  }

  void "should get TargetServerGroupContext with explicitly provided coordinates"() {
    given:
    def pipeline = new Pipeline()
    def stage = new Stage(pipeline, "", [
      target: [
        region         : "us-west-2",
        serverGroupName: "asg-v001",
        account        : "test",
        cloudProvider  : "aws"
      ]
    ])

    def siblingStage = new Stage(pipeline, "", [
      sourceServerGroupCapacitySnapshot: [
        min    : 10,
        max    : 20,
        desired: 15
      ]
    ])

    stage.parentStageId = "parentStageId"

    siblingStage.parentStageId = stage.parentStageId
    siblingStage.syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER

    pipeline.stages << stage
    pipeline.stages << siblingStage

    when:
    def targetServerGroupContext = task.getTargetServerGroupContext(stage)

    then:
    targetServerGroupContext.sourceServerGroupCapacitySnapshot == siblingStage.context.sourceServerGroupCapacitySnapshot
    targetServerGroupContext.context == [
      region         : "us-west-2",
      asgName        : "asg-v001",
      serverGroupName: "asg-v001",
      credentials    : "test",
      cloudProvider  : "aws"
    ]
  }

  void "should get TargetServerGroupContext from coordinates from upstream deploy stage"() {
    given:
    def pipeline = new Pipeline()
    def deployStage = new Stage(pipeline, "", [
      refId                            : "1",
      "deploy.server.groups"           : ["us-west-2a": ["asg-v001"]],
      region                           : "us-west-2",
      sourceServerGroupCapacitySnapshot: [
        min    : 10,
        max    : 20,
        desired: 15
      ],
    ])


    def childStage = new Stage(pipeline, "", [
      requisiteRefIds: ["1"],
      credentials    : "test"
    ])

    pipeline.stages << deployStage
    pipeline.stages << childStage

    when:
    def targetServerGroupContext = task.getTargetServerGroupContext(childStage)

    then:
    targetServerGroupContext.sourceServerGroupCapacitySnapshot == deployStage.context.sourceServerGroupCapacitySnapshot
    targetServerGroupContext.context == [
      region         : "us-west-2",
      asgName        : "asg-v001",
      serverGroupName: "asg-v001",
      credentials    : "test",
      cloudProvider  : "aws"
    ]
  }
}
