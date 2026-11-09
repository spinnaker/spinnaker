/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import retrofit2.mock.Calls
import spock.lang.Specification

import java.util.function.Supplier

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

/**
 * EcsNativeServerGroupCreator delegates to the classic EcsServerGroupCreator (whose
 * operation/artifact/image-resolution logic is covered by EcsServerGroupCreatorSpec) and only
 * reports a different cloud provider. This confirms the routing id, the delegation, and that both
 * creators coexist as Spring beans.
 */
class EcsNativeServerGroupCreatorSpec extends Specification {

  private static EcsNativeServerGroupCreator creator(ArtifactUtils artifactUtils, OortService oortService) {
    new EcsNativeServerGroupCreator(
        new EcsServerGroupCreator(artifactUtils, oortService, new ContextParameterProcessor(), new RetrySupport()))
  }

  def "reports cloudProvider ecs-native so ecs-native deploy/clone stages route here"() {
    given:
    def creator = creator(Stub(ArtifactUtils), Mock(OortService))

    expect:
    creator.getCloudProvider() == "ecs-native"
    !creator.isKatoResultExpected()
  }

  def "still reports the ecs health provider name, since results are cached and viewed through the existing ECS read stack"() {
    given:
    def creator = creator(Stub(ArtifactUtils), Mock(OortService))

    expect:
    creator.getHealthProviderName() == Optional.of("ecs")
  }

  def "classic and native creators are distinct Spring beans keyed by different cloud providers"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.registerBean(ArtifactUtils, { Stub(ArtifactUtils) } as Supplier<ArtifactUtils>)
    context.registerBean(OortService, { Mock(OortService) } as Supplier<OortService>)
    context.registerBean(ContextParameterProcessor, { new ContextParameterProcessor() } as Supplier<ContextParameterProcessor>)
    context.registerBean(RetrySupport, { new RetrySupport() } as Supplier<RetrySupport>)
    context.register(EcsServerGroupCreator, EcsNativeServerGroupCreator)

    when:
    context.refresh()
    def creators = context.getBeansOfType(ServerGroupCreator).values()

    then:
    creators*.cloudProvider.sort() == ["ecs", "ecs-native"]
    // CreateServerGroupTask selects the creator by cloudProvider; each id must map to one creator.
    creators.find { it.cloudProvider == "ecs" } instanceof EcsServerGroupCreator
    creators.find { it.cloudProvider == "ecs-native" } instanceof EcsNativeServerGroupCreator

    cleanup:
    context.close()
  }

  def "delegates task definition artifact resolution to the classic creator"() {
    given:
    ArtifactUtils mockResolver = Stub(ArtifactUtils)
    OortService oortService = Mock()
    def creator = creator(mockResolver, oortService)
    def testArtifactId = "aaaa-bbbb-cccc-dddd"
    def taskDefArtifact = [artifactId: testArtifactId]
    Artifact resolvedArtifact = Artifact.builder().type('s3/object').name('s3://testfile.json').build()
    mockResolver.getBoundArtifactForStage(_, testArtifactId, null) >> resolvedArtifact
    def testDescription = [fromTrigger: "true", registry: "myregistry.io", repository: "myrepo", tag: "latest"]
    def testMappings = [
        [containerName: "web", imageDescription: testDescription],
        [containerName: "logs", imageDescription: testDescription],
    ]

    def stage = stage {}
    stage.execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, 'ecs')
    stage.context.credentials = "testUser"
    stage.context.application = "ecs"
    stage.context.useTaskDefinitionArtifact = true
    stage.context.evaluateTaskDefinitionArtifactExpressions = true
    stage.context.taskDefinitionArtifact = taskDefArtifact
    stage.context.containerMappings = testMappings
    stage.execution.trigger.parameters.put("tg", "bar")

    when:
    def operations = creator.getOperations(stage)

    then:
    1 * oortService.fetchArtifact(*_) >> Calls.response(
        ResponseBody.create(MediaType.parse("application/json"), '{"foo": "${ parameters[\'tg\'] }"}'))
    0 * oortService._
    operations[0].createServerGroup.spelProcessedTaskDefinitionArtifact.toString() == "[foo:bar]"
    operations[0].createServerGroup.containerToImageMap.keySet() == ["web", "logs"] as Set
  }
}
