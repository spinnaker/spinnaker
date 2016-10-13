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


package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityTask
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
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
    parentPipeline.stages << new PipelineStage(parentPipeline, "", [
      strategy                         : "custom",
      sourceServerGroupCapacitySnapshot: [
        min    : 0,
        desired: 5,
        max    : 10
      ]
    ])
    parentPipeline.stages << new PipelineStage(parentPipeline, "", [
      executionId: "execution-id"
    ])
    ((AbstractStage) parentPipeline.stages[0]).id = "stage-1"
    ((AbstractStage) parentPipeline.stages[1]).parentStageId = "stage-1"
    ((AbstractStage) parentPipeline.stages[1]).type = "pipeline"

    def childPipeline = new Pipeline()
    childPipeline.stages << new PipelineStage(childPipeline, "", [
      type: "doSomething"
    ])
    childPipeline.stages << new PipelineStage(childPipeline, "", [
      type: "createServerGroup"
    ])
    childPipeline.stages << new PipelineStage(childPipeline, "", [
      type                  : "createServerGroup",
      "deploy.server.groups": [
        "us-west-1": ["asg-v001"]
      ]
    ])

    (parentPipeline.stages + childPipeline.stages).each {
      ((AbstractStage) it).setStageNavigator(stageNavigator)
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
    def stage = new PipelineStage(new Pipeline(), "", [
      strategy                         : strategy,
      sourceServerGroupCapacitySnapshot: [
        min    : 0,
        desired: 5,
        max    : 10
      ]
    ])
    ((AbstractStage) stage).setStageNavigator(stageNavigator)

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
    def pipeline = new Pipeline()
    pipeline.stages << new PipelineStage(pipeline, "", [
      "deploy.server.groups"           : [
        "us-east-1": ["application-stack-v001"]
      ],
      application                      : "application",
      stack                            : "stack",
      account                          : "test",
      region                           : "us-east-1",
      sourceServerGroupCapacitySnapshot: [
        min    : originalMinCapacity,
        desired: 10,
        max    : 20
      ]
    ])
    pipeline.stages << new PipelineStage(pipeline, "", [account: "test"])
    pipeline.stages.each {
      ((AbstractStage) it).setStageNavigator(stageNavigator)
    }

    ((AbstractStage) pipeline.stages[0]).id = "stage-1"
    ((AbstractStage) pipeline.stages[1]).parentStageId = "stage-1"

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
    def result = task.convert(pipeline.stages[-1])

    then:
    1 * oortHelper.getTargetServerGroup(
      "test",
      "application-stack-v001",
      "us-east-1",
      "aws"
    ) >> Optional.of(targetServerGroup)

    result == [
      credentials    : "test",
      asgName        : "application-stack-v001",
      serverGroupName: "application-stack-v001",
      regions        : ["us-east-1"],
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
}
