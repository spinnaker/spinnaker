/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import retrofit.client.Response
import retrofit.mime.TypedString
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeployCloudFormationTaskSpec extends Specification {

  def katoService = Mock(KatoService)
  def oortService = Mock(OortService)
  def objectMapper = new ObjectMapper()
  def artifactResolver = Mock(ArtifactResolver)

  @Subject
  def deployCloudFormationTask = new DeployCloudFormationTask(katoService: katoService, oortService: oortService,  artifactResolver: artifactResolver)

  def "should put kato task information as output"() {
    given:
    def taskId = new TaskId(id: 'id')
    def pipeline = Execution.newPipeline('orca')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      source: 'text',
      regions: ['eu-west-1'],
      templateBody: [key: 'value']]
    def stage = new Stage(pipeline, 'test', 'test', context)

    when:
    def result = deployCloudFormationTask.execute(stage)

    then:
    1 * katoService.requestOperations(_, _) >> Observable.just(taskId)
    result.context.'kato.result.expected' == true
    result.context.'kato.last.task.id' == taskId
  }

  @Unroll
  def "should fail if context is invalid"() {
    given:
    def pipeline = Execution.newPipeline('orca')
    def template = new TypedString('{ "key": "value" }')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      source: source,
      stackArtifactId: stackArtifactId,
      stackArtifactAccount: stackArtifactAccount,
      templateBody: templateBody,
      regions: regions
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)

    when:
    def result = deployCloudFormationTask.execute(stage)

    then:
    (_..1) * artifactResolver.getBoundArtifactForId(stage, 'id') >> new Artifact()
    (_..1) * oortService.fetchArtifact(_) >> new Response("url", 200, "reason", Collections.emptyList(), template)
    thrown(expectedException)

    where:
    source     | stackArtifactId | stackArtifactAccount | templateBody   | regions || expectedException
    null       | null            | null                 | null           | null    || IllegalArgumentException
    'artifact' | null            | null                 | null           | null    || IllegalArgumentException
    'artifact' | 'id'            | null                 | null           | null    || IllegalArgumentException
    'artifact' | 'id'            | 'account'            | null           | []      || IllegalArgumentException
    'text'     | null            | null                 | null           | null    || IllegalArgumentException
    'text'     | null            | null                 | [] as Map      | null    || IllegalArgumentException
    'text'     | null            | null                 | [key: 'value'] | []      || IllegalArgumentException
  }

  def "should fetch artifact if specified, and add it as a templateBody"() {
    given:
    def taskId = new TaskId(id: 'id')
    def pipeline = Execution.newPipeline('orca')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      source: source,
      stackArtifactId: stackArtifactId,
      stackArtifactAccount: stackArtifactAccount,
      regions: ['eu-west-1'],
      templateBody: [key: 'value']]
    def stage = new Stage(pipeline, 'test', 'test', context)

    when:
    def result = deployCloudFormationTask.execute(stage)

    then:
    1 * artifactResolver.getBoundArtifactForId(stage, 'id') >> new Artifact()
    1 * oortService.fetchArtifact(_) >> new Response("url", 200, "reason", Collections.emptyList(), new TypedString(template))
    1 * katoService.requestOperations("aws", {
      it.get(0).get("deployCloudFormation").containsKey("templateBody")
    }) >> Observable.just(taskId)
    result.context.'kato.result.expected' == true
    result.context.'kato.last.task.id' == taskId

    where:
    source     | stackArtifactId  | stackArtifactAccount | template
    'artifact' | 'id'             | 'account'            | '{"key": "value"}'
    'artifact' | 'id'             | 'account'            | 'key: value'
  }

}
