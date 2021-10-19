/*
 * Copyright 2021 OpsMx, Inc.
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
package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.PipelineModelMutator
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class DeletePipelineTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  PipelineModelMutator mutator = Mock()

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  SavePipelineTask sTask = new SavePipelineTask(Optional.of(front50Service), Optional.of([mutator]), objectMapper)

  @Subject
  DeletePipelineTask task = new DeletePipelineTask(Optional.of(front50Service), Optional.of([mutator]), objectMapper)

  def "should delete the pipeline"() {
    given:
    def pipeline = [
        application: 'orca',
        name: 'my pipeline',
        stages: []
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    sTask.execute(stage)
    def result = task.execute(stage)

    then:
    2 * mutator.supports(pipeline) >> true
    2 * mutator.mutate(pipeline)
    1 * front50Service.savePipeline(pipeline, _) >> {
      new Response('http://front50', 200, 'OK', [], null)
    }
    1 * front50Service.deletePipeline(pipeline.application, pipeline.name) >> {
      new Response('http://front50', 200, 'OK', [], null)
    }
    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.copyOf([
        'notification.type': 'deletepipeline',
        'application': 'orca',
        'pipeline.name': 'my pipeline'
    ])
  }

  def "should fail task when front 50 delete call fails"() {
    given:
    def pipeline = [
        application: 'orca',
        name: 'my pipeline',
        stages: []
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    front50Service.getPipelines(_) >> []
    front50Service.deletePipeline(_, _) >> {
      new Response('http://front50', 500, 'OK', [], null)
    }
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
  }
}
