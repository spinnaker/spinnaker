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
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class EcsServerGroupCreatorSpec extends Specification {

  @Subject
  ArtifactUtils mockResolver
  EcsServerGroupCreator creator
  OortService oortService = Mock()
  ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()
  def stage = stage {}

  def deployConfig = [
    credentials: "testUser",
    application: "ecs"
  ]

  def setup() {
    mockResolver = Stub(ArtifactUtils)
    creator = new EcsServerGroupCreator(mockResolver, oortService, contextParameterProcessor, new RetrySupport())
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
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
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
    stage.execution = new PipelineExecutionImpl(ExecutionType.ORCHESTRATION, 'ecs')
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
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
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
    Artifact resolvedArtifact = Artifact.builder().type('s3/object').name('s3://testfile.json').build()
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
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
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

  def "creates operation from downloaded and SpEL processed artifact with no parameter value available"() {
    given:
    def testArtifactId = createTaskdefArtifactId()
    def taskDefArtifact = createTaskDefArtifact(testArtifactId)

    Artifact resolvedArtifact = createResolvedArtifact()
    mockResolver.getBoundArtifactForStage(stage, testArtifactId, null) >> resolvedArtifact
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = createTestDescription(testReg, testRepo, testTag)
    def testMappings = []
    def map1 = createMap1(testDescription)
    def map2 = createMap2(testDescription)
    testMappings.add(map1)
    testMappings.add(map2)

    // add inputs to stage context
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
    stage.context.useTaskDefinitionArtifact = true
    stage.context.evaluateTaskDefinitionArtifactExpressions = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings
    stage.execution.trigger.parameters.put("notFound", "noValue")

    when:
    def operations = creator.getOperations(stage)

    then:

    1 * oortService.fetchArtifact(*_) >> new retrofit.client.Response('http://oort.com', 200, 'Okay', [], new TypedString(response))
    0 * oortService._
    operations[0].createServerGroup.spelProcessedTaskDefinitionArtifact.toString().equals(expected)

    where:
    response                                               | expected
    '{"foo": "${ parameters[\'tg\'] ?: \'noValue\' }"}'    | "[foo:noValue]"
  }

  def "creates operation from downloaded and SpEL processed artifact"() {
    given:
    def testArtifactId = createTaskdefArtifactId()
    def taskDefArtifact = createTaskDefArtifact(testArtifactId)

    Artifact resolvedArtifact = createResolvedArtifact()
    mockResolver.getBoundArtifactForStage(stage, testArtifactId, null) >> resolvedArtifact
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = createTestDescription(testReg, testRepo, testTag)
    def testMappings = []
    def map1 = createMap1(testDescription)
    def map2 = createMap2(testDescription)
    testMappings.add(map1)
    testMappings.add(map2)

    // add inputs to stage context
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
    stage.context.useTaskDefinitionArtifact = true
    stage.context.evaluateTaskDefinitionArtifactExpressions = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings
    stage.execution.trigger.parameters.put("tg", "bar")

    when:
    def operations = creator.getOperations(stage)

    then:

    1 * oortService.fetchArtifact(*_) >> new retrofit.client.Response('http://oort.com', 200, 'Okay', [], new TypedString(response))
    0 * oortService._
    operations[0].createServerGroup.spelProcessedTaskDefinitionArtifact.toString().equals(expected)

    where:
    response                                               | expected
    '{"foo": "${ parameters[\'tg\'] }"}'                   | "[foo:bar]"
    '{"foo": "${ #toInt(\'43\') }"}'                       | "[foo:43]"
  }

  def "creates operation when evaluateTaskDefinitionArtifactExpressions flag is falsy"() {
    given:
    // define artifact inputs
    def testArtifactId = createTaskdefArtifactId()
    def taskDefArtifact = createTaskDefArtifact(testArtifactId)

    Artifact resolvedArtifact = createResolvedArtifact()
    mockResolver.getBoundArtifactForStage(stage, testArtifactId, null) >> resolvedArtifact
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = createTestDescription(testReg, testRepo, testTag)
    def testMappings = []
    def map1 = createMap1(testDescription)
    def map2 = createMap2(testDescription)
    testMappings.add(map1)
    testMappings.add(map2)

    def containerToImageMap = [
      web: "$testReg/$testRepo:$testTag",
      logs: "$testReg/$testRepo:$testTag"
    ]

    // add inputs to stage context
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
    stage.context.useTaskDefinitionArtifact = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings
    stage.context.evaluateTaskDefinitionArtifactExpressions = false

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

  def "creates operation when evaluateTaskDefinitionArtifactExpressions is truthy but there is no expression"() {
    given:
    def testArtifactId = createTaskdefArtifactId()
    def taskDefArtifact = createTaskDefArtifact(testArtifactId)

    Artifact resolvedArtifact = createResolvedArtifact()
    mockResolver.getBoundArtifactForStage(stage, testArtifactId, null) >> resolvedArtifact
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = createTestDescription(testReg, testRepo, testTag)
    def testMappings = []
    def map1 = createMap1(testDescription)
    def map2 = createMap2(testDescription)
    testMappings.add(map1)
    testMappings.add(map2)

    // add inputs to stage context
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
    stage.context.useTaskDefinitionArtifact = true
    stage.context.evaluateTaskDefinitionArtifactExpressions = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings

    when:
    def operations = creator.getOperations(stage)

    then:

    1 * oortService.fetchArtifact(*_) >> new retrofit.client.Response('http://oort.com', 200, 'Okay', [], new TypedString(response))
    0 * oortService._
    operations[0].createServerGroup.spelProcessedTaskDefinitionArtifact.toString().equals(expected)

    where:
    response                                               | expected
    '{"foo": "bar"}'                                       | "[foo:bar]"
  }

  def "creates operation when evaluateTaskDefinitionArtifactExpressions is truthy with environment variables"() {
    given:
    def testArtifactId = createTaskdefArtifactId()
    def taskDefArtifact = createTaskDefArtifact(testArtifactId)

    Artifact resolvedArtifact = createResolvedArtifact()
    mockResolver.getBoundArtifactForStage(stage, testArtifactId, null) >> resolvedArtifact
    def (testReg,testRepo,testTag) = ["myregistry.io","myrepo","latest"]
    def testDescription = createTestDescription(testReg, testRepo, testTag)
    def testMappings = []
    def map1 = createMap1(testDescription)
    def map2 = createMap2(testDescription)
    testMappings.add(map1)
    testMappings.add(map2)

    // add inputs to stage context
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
    stage.context.useTaskDefinitionArtifact = true
    stage.context.evaluateTaskDefinitionArtifactExpressions = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings
    stage.execution.trigger.parameters.put("tg", "bar")
    stage.execution.trigger.parameters.put("ENV1", "bar")
    stage.context.name = "Deploy"

    when:
    def operations = creator.getOperations(stage)

    then:

    1 * oortService.fetchArtifact(*_) >> new retrofit.client.Response('http://oort.com', 200, 'Okay', [], new TypedString(response))
    0 * oortService._
    operations[0].createServerGroup.spelProcessedTaskDefinitionArtifact.toString().equals(expected)

    where:
    response                                               | expected
    '{"foo": "${ENV1}.b.${ENV2}"}'                         | "[foo:\${ENV1}.b.\${ENV2}]"
    '{"foo": "${ parameters[\'tg\'] }"}'                   | "[foo:bar]"
    '{"foo": "${ #toInt(\'80\') }"}'                       | "[foo:80]"
  }


  def createTaskdefArtifactId(){
    return "aaaa-bbbb-cccc-dddd"
  }

  def createTaskDefArtifact(String testArtifactId){
    return [
        artifactId: testArtifactId
    ]
  }

  def createResolvedArtifact(){
    return Artifact.builder().type('s3/object').name('s3://testfile.json').build()
  }

  def createTestDescription(String testReg, String testRepo, String testTag){
    return [
        fromTrigger: "true",
        registry: testReg,
        repository: testRepo,
        tag: testTag
    ]
  }

  def createMap1(testDescription){
    return [
        containerName: "web",
        imageDescription: testDescription
    ]
  }

  def createMap2(testDescription){
    return [
        containerName: "logs",
        imageDescription: testDescription
    ]
  }
}
