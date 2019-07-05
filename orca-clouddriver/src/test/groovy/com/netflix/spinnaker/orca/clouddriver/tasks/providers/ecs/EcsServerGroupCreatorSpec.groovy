/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.google.common.collect.Maps
import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class EcsServerGroupCreatorSpec extends Specification {

  @Subject
  ArtifactResolver mockResolver
  EcsServerGroupCreator creator
  def stage = stage {}

  def deployConfig = [
    credentials: "testUser",
    application: "ecs"
  ]

  def setup() {
    mockResolver = Stub(ArtifactResolver)
    creator = new EcsServerGroupCreator(mockResolver)
    stage.execution.stages.add(stage)
    stage.context = deployConfig
  }

  def cleanup() {
    stage.execution.stages.clear()
    stage.execution.stages.add(stage)
  }

  def "creates operation from trigger image"() {
    given:
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = [
      fromTrigger: "true",
      registry: testReg,
      repository: testRepo,
      tag: testTag
    ]
    stage.context.imageDescription = testDescription
    stage.execution = new Execution(ExecutionType.PIPELINE, 'ecs')
    def expected = Maps.newHashMap(deployConfig)
    expected.dockerImageAddress = "$testReg/$testRepo:$testTag"

    when:
    def operations = creator.getOperations(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup == expected
  }

  def "creates operation from context image"() {
    given:
    def (testReg, testRepo, testTag) = ["myregistry.io", "myrepo", "latest"]
    def parentStageId = "PARENTID123"
    def testDescription = [
      fromContext: "true",
      stageId    : parentStageId,
      registry   : testReg,
      repository : testRepo,
      tag        : testTag
    ]

    def parentStage = stage {}
    parentStage.id = parentStageId
    parentStage.refId = parentStageId
    parentStage.context.amiDetails = [imageId: [value: ["$testReg/$testRepo:$testTag"]]]

    stage.context.imageDescription = testDescription
    stage.parentStageId = parentStageId
    stage.execution = new Execution(ExecutionType.ORCHESTRATION, 'ecs')
    stage.execution.stages.add(parentStage)

    def expected = Maps.newHashMap(deployConfig)
    expected.dockerImageAddress = "$testReg/$testRepo:$testTag"

    when:
    def operations = creator.getOperations(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup == expected
  }

  def "creates operation from previous 'find image from tags' stage"() {
    given:
    def (testReg, testRepo, testTag, testRegion) = ["myregistry.io", "myrepo", "latest", "us-west-2"]
    def parentStageId = "PARENTID123"

    def parentStage = stage {}
    parentStage.id = parentStageId
    parentStage.context.region = testRegion
    parentStage.context.cloudProviderType = "ecs"
    parentStage.context.amiDetails = [imageId: [value: ["$testReg/$testRepo:$testTag"]]]

    stage.context.region = testRegion
    stage.parentStageId = parentStageId
    stage.execution = new Execution(ExecutionType.PIPELINE, 'ecs')
    stage.execution.stages.add(parentStage)

    def expected = Maps.newHashMap(deployConfig)
    expected.dockerImageAddress = "$testReg/$testRepo:$testTag"

    when:
    def operations = creator.getOperations(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup == expected
  }

  def "creates operation from taskDefinitionArtifact provided as artifact"() {
    given:
    // define artifact inputs
    def testArtifactId = "aaaa-bbbb-cccc-dddd"
    def taskDefArtifact = [
      artifactId: testArtifactId
    ]
    Artifact resolvedArtifact = new Artifact().builder().type('s3/object').name('s3://testfile.json').build()
    mockResolver.getBoundArtifactForStage(stage, testArtifactId, null) >> resolvedArtifact
    // define container mappings inputs
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = [
      fromTrigger: "true",
      registry: testReg,
      repository: testRepo,
      tag: testTag
    ]
    def testMappings = []
    def map1 = [
      containerName: "web",
      imageDescription: testDescription
    ]
    def map2 = [
      containerName: "logs",
      imageDescription: testDescription
    ]
    testMappings.add(map1)
    testMappings.add(map2)

    def containerToImageMap = [
      web: "$testReg/$testRepo:$testTag",
      logs: "$testReg/$testRepo:$testTag"
    ]

    // add inputs to stage context
    stage.execution = new Execution(ExecutionType.PIPELINE, 'ecs')
    stage.context.useTaskDefinitionArtifact = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings

    def expected = Maps.newHashMap(deployConfig)
    expected.resolvedTaskDefinitionArtifact = resolvedArtifact
    expected.containerToImageMap = containerToImageMap

    when:
    def operations = creator.getOperations(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup == expected
  }
}
