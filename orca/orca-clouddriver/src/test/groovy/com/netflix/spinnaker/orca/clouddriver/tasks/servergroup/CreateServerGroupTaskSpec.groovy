/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.api.operations.OperationsRunner
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.model.KatoOperationsContext
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.AmazonServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce.GoogleServerGroupCreator
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CreateServerGroupTaskSpec extends Specification {

  @Shared
  ServerGroupCreator aCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "aCloud"
    isKatoResultExpected() >> false
    getOperations(_) >> [["aOp": "foo"]]
  }
  @Shared
  ServerGroupCreator bCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "bCloud"
    isKatoResultExpected() >> false
    getOperations(_) >> [["bOp": "bar"]]
  }
  @Shared
  ServerGroupCreator cCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "cCloud"
    isKatoResultExpected() >> true
    getOperations(_) >> [["cOp": "baz"]]
  }
  @Shared
  TaskId taskId = new TaskId(UUID.randomUUID().toString())

  @Shared
  def baseOutput = [
    "notification.type"  : "createdeploy",
    "kato.last.task.id"  : taskId,
    "deploy.account.name": "abc"
  ]

  @Shared mapper = OrcaObjectMapper.newInstance()

  @Unroll
  def "should have cloud provider-specific outputs"() {
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    def task = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: [aCreator, bCreator, cCreator])
    def stage = ExecutionBuilder.stage {
      type = "whatever"
      context["credentials"] = "abc"
      context["cloudProvider"] = cloudProvider
    }

    when:
    def result = task.execute(stage)

    then:
    1 * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result
    result.context == outputs

    where:
    cloudProvider | ops              || outputs
    "aCloud"      | [["aOp": "foo"]] || baseOutput + ["kato.result.expected": false]
    "bCloud"      | [["bOp": "bar"]] || baseOutput + ["kato.result.expected": false]
    "cCloud"      | [["cOp": "baz"]] || baseOutput + ["kato.result.expected": true]
  }

  @Unroll
  def "image baked for #bakeCloudProvider is resolved by create stage for #operationCloudProvider"() {
    /*
      bake -> createServerGroup
     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def pipeline = ExecutionBuilder.pipeline {}

    def bakeStage1 = buildStageForPipeline(pipeline, "bake")

    def bakeSynthetic1 = buildStageForPipeline(pipeline, "bake in $deployRegion", buildBakeConfig("some-ami-name", deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic1, bakeStage1)

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(pipeline, "createServerGroup", deployConfig)
    deployStage.context.deploymentDetails = [
      ["imageId": "not-my-ami", "ami": "not-my-ami", "region": deployRegion],
      ["imageId": "also-not-my-ami", "ami": "also-not-my-ami", "region": deployRegion]
    ]
    makeDependentOn(deployStage, bakeStage1)

    def deployTask = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | true               || "some-ami-name"
    "gce"                  | null              | "image"           | true               || "some-ami-name"
    "aws"                  | "aws"             | "imageId"         | true               || "some-ami-name"
    "aws"                  | null              | "imageId"         | true               || "some-ami-name"
  }

  @Unroll
  def "image for #bakeCloudProvider in parent pipeline deployment details is resolved by create stage for #operationCloudProvider"() {
    /*
      parentPipeline: deploymentDetails
                                        | trigger
                                        v
                                          childPipeline: manualJudgment -> createServerGroup
     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentDeploymentDetails = [
      deploymentDetails: [
        ["imageId": "parent-ami", "ami": "parent-ami", "region": deployRegion]
      ]
    ]
    def parentPipeline = pipeline {
      name = "parent"
      stage {
        type = "someStage"
        outputs.putAll(parentDeploymentDetails)
      }
    }

    def childPipeline = pipeline {
      name = "child"
      trigger = new PipelineTrigger(parentPipeline)
    }
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | true               || "parent-ami"
    "gce"                  | null              | "image"           | true               || "parent-ami"
    "aws"                  | "aws"             | "imageId"         | true               || "parent-ami"
    "aws"                  | null              | "imageId"         | true               || "parent-ami"
  }

  @Unroll
  def "image for #bakeCloudProvider in grandparent pipeline deployment details is resolved by create stage for #operationCloudProvider"() {
    /*
      grandparentPipeline: deploymentDetails
                                             | trigger
                                             v
                                               parentPipeline:
                                                               | trigger
                                                               v
                                                                 childPipeline: manualJudgment -> createServerGroup
     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def grandparentDeploymentDetails = [
      deploymentDetails: [
        ["imageId": "grandparent-ami", "ami": "grandparent-ami", "region": deployRegion]
      ]
    ]
    // Building these as maps instead of using the pipeline model objects since this configuration was observed in testing.
    def parentTrigger = [
      type           : "pipeline",
      isPipeline     : true,
      parentExecution: mapper.convertValue(pipeline {
        name = "grandparent"
        stage {
          type = "someStage1"
          outputs.putAll(grandparentDeploymentDetails)
        }
        stage { type = "someStage2" }
      }, Map)
    ]

    def childTrigger = [
      type: "pipeline",
      isPipeline     : true,
      parentExecution: pipeline {
        name = "parent"
        trigger = mapper.convertValue(parentTrigger, Trigger)
      }
    ]
    def childPipeline = pipeline {
      name = "child"
      trigger = mapper.convertValue(childTrigger, Trigger)
    }
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | true               || "grandparent-ami"
    "gce"                  | null              | "image"           | true               || "grandparent-ami"
    "aws"                  | "aws"             | "imageId"         | true               || "grandparent-ami"
    "aws"                  | null              | "imageId"         | true               || "grandparent-ami"
  }

  @Unroll
  def "image for #bakeCloudProvider in parent pipeline stage context is resolved by create stage for #operationCloudProvider"() {
    /*
      parentPipeline: bake
                           | trigger
                           v
                             childPipeline: manualJudgment -> createServerGroup
     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentPipeline = pipeline { name = "parent" }

    def bakeStage1 = buildStageForPipeline(parentPipeline, "bake")

    def bakeSynthetic1 = buildStageForPipeline(parentPipeline, "bake in $deployRegion", buildBakeConfig("parent-name", deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic1, bakeStage1)

    def childTrigger = [
      type: "pipeline",
      parentExecution: parentPipeline
    ]
    def childPipeline = pipeline {
      name = "child"
      trigger = mapper.convertValue(childTrigger, Trigger)
    }
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | true               || "parent-name"
    "gce"                  | null              | "image"           | true               || "parent-name"
    "aws"                  | "aws"             | "imageId"         | true               || "parent-name"
    "aws"                  | null              | "imageId"         | true               || "parent-name"
  }

  @Unroll
  def "image for #bakeCloudProvider in grandparent pipeline stage context is resolved by create stage for #operationCloudProvider"() {
    /*
      grandparentPipeline: bake
                           | trigger
                           v
                             parentPipeline:
                                             | trigger
                                             v
                                               childPipeline: manualJudgment -> createServerGroup
     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def grandparentPipeline = pipeline { name = "grandparent" }

    def bakeStage1 = buildStageForPipeline(grandparentPipeline, "bake")

    def bakeSynthetic1 = buildStageForPipeline(grandparentPipeline, "bake in $deployRegion", buildBakeConfig("grandparent-name", deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic1, bakeStage1)

    def parentTrigger = [
      type: "pipeline",
      parentExecution: grandparentPipeline
    ]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = mapper.convertValue(parentTrigger, Trigger)
    }

    def childTrigger = [
      type: "pipeline",
      parentExecution: parentPipeline
    ]
    def childPipeline = pipeline {
      name = "child"
      trigger = mapper.convertValue(childTrigger, Trigger)
    }
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | true               || "grandparent-name"
    "gce"                  | null              | "image"           | true               || "grandparent-name"
    "aws"                  | "aws"             | "imageId"         | true               || "grandparent-name"
    "aws"                  | null              | "imageId"         | true               || "grandparent-name"
  }

  @Unroll
  def "image for #bakeCloudProvider in parent pipeline deployment details is resolved by create stage for #operationCloudProvider when child pipeline is triggered via pipeline stage in the parent"() {
    /*
      parentPipeline: deploymentDetails -> pipelineStage
                                                         |
                                                         v
                                                           childPipeline: manualJudgment -> createServerGroup
     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentDeploymentDetails = [
      deploymentDetails: [
        ["imageId": "parent-ami", "ami": "parent-ami", "region": deployRegion]
      ]
    ]
    def parentPipeline = pipeline {
      name = "parent"
      stage {
        type = "someStage"
        outputs.putAll(parentDeploymentDetails)
      }
    }

    def pipelineStage = buildStageForPipeline(parentPipeline, "pipeline")

    def childTrigger = [
      type: "pipeline",
      parentExecution      : parentPipeline,
      parentPipelineStageId: pipelineStage.id
    ]
    def childPipeline = pipeline {
      name = "child"
      trigger = mapper.convertValue(childTrigger, Trigger)
    }
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    result?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | true               || "parent-ami"
    "gce"                  | null              | "image"           | true               || "parent-ami"
    "aws"                  | "aws"             | "imageId"         | true               || "parent-ami"
    "aws"                  | null              | "imageId"         | true               || "parent-ami"
  }

  @Unroll
  def "images for #bakeCloudProvider in parent pipeline stage contexts in parallel branches are resolved by create stage for #operationCloudProvider"() {
    /*
                                bake1 -> pipelineStageA
                              /                         |
      parentPipeline: wait ->                           v
                              \                           childPipelineA: manualJudgmentA -> createServerGroupA
                                bake2 -> pipelineStageB
                                                        |
                                                        v
                                                          childPipelineB: manualJudgmentB -> createServerGroupB


     */
    given:
    OperationsRunner operationsRunner = Mock(OperationsRunner)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentPipeline = pipeline { name = "parent" }

    def waitStage = buildStageForPipeline(parentPipeline, "wait")

    def bakeStage1 = buildStageForPipeline(parentPipeline, "bake")
    makeDependentOn(bakeStage1, waitStage)

    def bakeSynthetic1 = buildStageForPipeline(parentPipeline, "bake in $deployRegion", buildBakeConfig(expectedImageIdBranchA, deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic1, bakeStage1)

    def bakeStage2 = buildStageForPipeline(parentPipeline, "bake")
    makeDependentOn(bakeStage2, waitStage)

    def bakeSynthetic2 = buildStageForPipeline(parentPipeline, "bake in $deployRegion", buildBakeConfig(expectedImageIdBranchB, deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic2, bakeStage2)

    def pipelineStageA = buildStageForPipeline(parentPipeline, "pipeline")
    makeDependentOn(pipelineStageA, bakeStage1)

    def childTriggerA = [
      type: "pipeline",
      parentExecution      : parentPipeline,
      parentPipelineStageId: pipelineStageA.id
    ]
    def childPipelineA = pipeline {
      name = "child"
      trigger = mapper.convertValue(childTriggerA, Trigger)
    }
    def manualJudgmentStageA = buildStageForPipeline(childPipelineA, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStageA = buildStageForPipeline(childPipelineA, "createServerGroup", deployConfig)
    makeDependentOn(deployStageA, manualJudgmentStageA)

    def deployTaskA = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    def pipelineStageB = buildStageForPipeline(parentPipeline, "pipeline")
    makeDependentOn(pipelineStageB, bakeStage2)

    def childTriggerB = [
      type: "pipeline",
      parentExecution      : parentPipeline,
      parentPipelineStageId: pipelineStageB.id
    ]
    def childPipelineB = pipeline {
      name = "child"
      trigger = mapper.convertValue(childTriggerB, Trigger)
    }
    def manualJudgmentStageB = buildStageForPipeline(childPipelineB, "manualJudgment")

    def deployStageB = buildStageForPipeline(childPipelineB, "createServerGroup", deployConfig)
    makeDependentOn(deployStageB, manualJudgmentStageB)

    def deployTaskB = new CreateServerGroupTask(operationsRunner: operationsRunner, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def resultA = deployTaskA.execute(deployStageA)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageIdBranchA
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    resultA?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    when:
    def resultB = deployTaskB.execute(deployStageB)

    then:
    _ * mortService.getAccountDetails("abc") >> Calls.response([:])
    1 * operationsRunner.run(_) >> {
      OperationsInput operationsInput = it[0]
      operationsInput.getOperations()[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageIdBranchB
      new KatoOperationsContext(taskId, null)
    }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * operationsRunner.run(_) >> { new KatoOperationsContext(taskId, null) }
    resultB?.context == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageIdBranchA | expectedImageIdBranchB
    "gce"                  | "gce"             | "image"           | true               || "parent-name-branch-a" | "parent-name-branch-b"
    "gce"                  | null              | "image"           | true               || "parent-name-branch-a" | "parent-name-branch-b"
    "aws"                  | "aws"             | "imageId"         | true               || "parent-name-branch-a" | "parent-name-branch-b"
    "aws"                  | null              | "imageId"         | true               || "parent-name-branch-a" | "parent-name-branch-b"
  }

  private
  def buildBakeConfig(String imageId, String deployRegion, String cloudProvider) {
    return [
      imageId      : imageId,
      ami          : imageId,
      region       : deployRegion,
      cloudProvider: cloudProvider
    ]
  }

  private
  def buildDeployConfig(String deployRegion, String operationCloudProvider) {
    return [
      application      : "hodor",
      cloudProvider    : operationCloudProvider,
      instanceType     : "large",
      securityGroups   : ["a", "b", "c"],
      region           : deployRegion,
      availabilityZones: [(deployRegion): ["a", "d"]],
      capacity         : [
        min    : 1,
        max    : 20,
        desired: 5
      ],
      credentials      : "abc"
    ]
  }

  private def buildStageForPipeline(
    def pipeline, String stageType, def context = [:]) {
    def stage = new StageExecutionImpl(pipeline, stageType, context)

    pipeline.stages << stage

    return stage
  }

  private void makeDependentOn(StageExecutionImpl dependent, StageExecutionImpl dependency) {
    if (!dependency.refId) {
      dependency.refId = UUID.randomUUID()
    }

    dependent.requisiteStageRefIds = [dependency.refId]
  }

  private void makeChildOf(StageExecutionImpl child, StageExecutionImpl parent) {
    child.parentStageId = parent.id
  }

  private def buildServerGroupCreators(MortService mortService) {
    // set the default bake account to avoid dealing with an allowLaunch operation getting injected before
    // the createServerGroup operation
    return [new AmazonServerGroupCreator(mortService: mortService, defaultBakeAccount: "abc"), new GoogleServerGroupCreator()]
  }
}
