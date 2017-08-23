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

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.AmazonServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce.GoogleServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

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

  @Unroll
  def "should have cloud provider-specific outputs"() {
    given:
      KatoService katoService = Mock(KatoService)
      def task = new CreateServerGroupTask(kato: katoService, serverGroupCreators: [aCreator, bCreator, cCreator])
      def stage = new Stage<>(new Pipeline(), "whatever", [credentials: "abc", cloudProvider: cloudProvider])

    when:
      def result = task.execute(stage)

    then:
      1 * katoService.requestOperations(cloudProvider, ops) >> { Observable.from(taskId) }
      result
    result.stageOutputs == outputs

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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def pipeline = new Pipeline()

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

    def deployTask = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    result?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | false              || "some-ami-name"
    "gce"                  | null              | "image"           | false              || "some-ami-name"
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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentGlobalContext = [
      deploymentDetails: [
        ["imageId": "parent-ami", "ami": "parent-ami", "region": deployRegion]
      ]
    ]
    def parentPipeline = Pipeline.builder().withName("parent").withGlobalContext(parentGlobalContext).build()

    def childTrigger = [
      parentExecution: parentPipeline
    ]
    def childPipeline = Pipeline.builder().withName("child").withTrigger(childTrigger).build()
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    result?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | false              || "parent-ami"
    "gce"                  | null              | "image"           | false              || "parent-ami"
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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def grandparentGlobalContext = [
      deploymentDetails: [
        ["imageId": "grandparent-ami", "ami": "grandparent-ami", "region": deployRegion]
      ]
    ]
    // Building these as maps instead of using the pipeline model objects since this configuration was observed in testing.
    def parentTrigger = [
      isPipeline: true,
      parentExecution: [
        name: "grandparent",
        context: grandparentGlobalContext,
        stages: [
          [type: "someStage1"],
          [type: "someStage2"]
        ]
      ]
    ]

    def childTrigger = [
      isPipeline: true,
      parentExecution: [
        name: "parent",
        trigger: parentTrigger
      ]
    ]
    def childPipeline = Pipeline.builder().withName("child").withTrigger(childTrigger).build()
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    result?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | false              || "grandparent-ami"
    "gce"                  | null              | "image"           | false              || "grandparent-ami"
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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentPipeline = Pipeline.builder().withName("parent").build()

    def bakeStage1 = buildStageForPipeline(parentPipeline, "bake")

    def bakeSynthetic1 = buildStageForPipeline(parentPipeline, "bake in $deployRegion", buildBakeConfig("parent-name", deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic1, bakeStage1)

    def childTrigger = [
      parentExecution: parentPipeline
    ]
    def childPipeline = Pipeline.builder().withName("child").withTrigger(childTrigger).build()
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    result?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | false              || "parent-name"
    "gce"                  | null              | "image"           | false              || "parent-name"
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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def grandparentPipeline = Pipeline.builder().withName("grandparent").build()

    def bakeStage1 = buildStageForPipeline(grandparentPipeline, "bake")

    def bakeSynthetic1 = buildStageForPipeline(grandparentPipeline, "bake in $deployRegion", buildBakeConfig("grandparent-name", deployRegion, bakeCloudProvider))
    makeChildOf(bakeSynthetic1, bakeStage1)

    def parentTrigger = [
      parentExecution: grandparentPipeline
    ]
    def parentPipeline = Pipeline.builder().withName("parent").withTrigger(parentTrigger).build()

    def childTrigger = [
      parentExecution: parentPipeline
    ]
    def childPipeline = Pipeline.builder().withName("child").withTrigger(childTrigger).build()
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    result?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | false              || "grandparent-name"
    "gce"                  | null              | "image"           | false              || "grandparent-name"
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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentGlobalContext = [
      deploymentDetails: [
        ["imageId": "parent-ami", "ami": "parent-ami", "region": deployRegion]
      ]
    ]
    def parentPipeline = Pipeline.builder().withName("parent").withGlobalContext(parentGlobalContext).build()

    def pipelineStage = buildStageForPipeline(parentPipeline, "pipeline")

    def childTrigger = [
      parentExecution: parentPipeline,
      parentPipelineStageId: pipelineStage.id
    ]
    def childPipeline = Pipeline.builder().withName("child").withTrigger(childTrigger).build()
    def manualJudgmentStage = buildStageForPipeline(childPipeline, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStage = buildStageForPipeline(childPipeline, "createServerGroup", deployConfig)
    makeDependentOn(deployStage, manualJudgmentStage)

    def deployTask = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def result = deployTask.execute(deployStage)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageId
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    result?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageId
    "gce"                  | "gce"             | "image"           | false              || "parent-ami"
    "gce"                  | null              | "image"           | false              || "parent-ami"
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
    KatoService katoService = Mock(KatoService)
    MortService mortService = Mock(MortService)
    def deployRegion = "us-east-1"
    def parentPipeline = Pipeline.builder().withName("parent").build()

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
      parentExecution: parentPipeline,
      parentPipelineStageId: pipelineStageA.id
    ]
    def childPipelineA = Pipeline.builder().withName("child").withTrigger(childTriggerA).build()
    def manualJudgmentStageA = buildStageForPipeline(childPipelineA, "manualJudgment")

    def deployConfig = buildDeployConfig(deployRegion, operationCloudProvider)
    def deployStageA = buildStageForPipeline(childPipelineA, "createServerGroup", deployConfig)
    makeDependentOn(deployStageA, manualJudgmentStageA)

    def deployTaskA = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    def pipelineStageB = buildStageForPipeline(parentPipeline, "pipeline")
    makeDependentOn(pipelineStageB, bakeStage2)

    def childTriggerB = [
      parentExecution: parentPipeline,
      parentPipelineStageId: pipelineStageB.id
    ]
    def childPipelineB = Pipeline.builder().withName("child").withTrigger(childTriggerB).build()
    def manualJudgmentStageB = buildStageForPipeline(childPipelineB, "manualJudgment")

    def deployStageB = buildStageForPipeline(childPipelineB, "createServerGroup", deployConfig)
    makeDependentOn(deployStageB, manualJudgmentStageB)

    def deployTaskB = new CreateServerGroupTask(kato: katoService, serverGroupCreators: buildServerGroupCreators(mortService))

    when:
    def resultA = deployTaskA.execute(deployStageA)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageIdBranchA
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    resultA?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    when:
    def resultB = deployTaskB.execute(deployStageB)

    then:
    _ * mortService.getAccountDetails("abc") >> [:]
    1 * katoService.requestOperations(operationCloudProvider, {
      return it[0]?.createServerGroup?.get(imageAttributeKey) == expectedImageIdBranchB
    }) >> { Observable.from(taskId) }
    // This helps avoid an NPE within CreateServerGroupTask; this results in better error-reporting on a test failure.
    _ * katoService.requestOperations(operationCloudProvider, _) >> { Observable.from(taskId) }
    resultB?.stageOutputs == baseOutput + ["kato.result.expected": katoResultExpected]

    where:
    operationCloudProvider | bakeCloudProvider | imageAttributeKey | katoResultExpected || expectedImageIdBranchA | expectedImageIdBranchB
    "gce"                  | "gce"             | "image"           | false              || "parent-name-branch-a" | "parent-name-branch-b"
    "gce"                  | null              | "image"           | false              || "parent-name-branch-a" | "parent-name-branch-b"
    "aws"                  | "aws"             | "imageId"         | true               || "parent-name-branch-a" | "parent-name-branch-b"
    "aws"                  | null              | "imageId"         | true               || "parent-name-branch-a" | "parent-name-branch-b"
  }

  private def buildBakeConfig(String imageId, String deployRegion, String cloudProvider) {
    return [
      imageId      : imageId,
      ami          : imageId,
      region       : deployRegion,
      cloudProvider: cloudProvider
    ]
  }

  private def buildDeployConfig(String deployRegion, String operationCloudProvider) {
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

  private def buildStageForPipeline(def pipeline, String stageType, def context = [:]) {
    def stage = new Stage<>(pipeline, stageType, context)

    pipeline.stages << stage

    return stage
  }

  private void makeDependentOn(Stage<Pipeline> dependent, Stage<Pipeline> dependency) {
    if (!dependency.refId) {
      dependency.refId = UUID.randomUUID()
    }

    dependent.requisiteStageRefIds = [dependency.refId]
  }

  private void makeChildOf(Stage<Pipeline> child, Stage<Pipeline> parent) {
    child.parentStageId = parent.id
  }

  private def buildServerGroupCreators(MortService mortService) {
    // set the default bake account to avoid dealing with an allowLaunch operation getting injected before
    // the createServerGroup operation
    return [new AmazonServerGroupCreator(mortService: mortService, defaultBakeAccount: "abc"), new GoogleServerGroupCreator()]
  }
}
