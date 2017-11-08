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
package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.PipelineModelMutator
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class SavePipelineTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  PipelineModelMutator mutator = Mock()

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  SavePipelineTask task = new SavePipelineTask(front50Service: front50Service, objectMapper: objectMapper, pipelineModelMutators: [mutator])

  def "should run model mutators with correct context"() {
    given:
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      stages: []
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * mutator.supports(pipeline) >> true
    1 * mutator.mutate(pipeline)
    1 * front50Service.savePipeline(pipeline) >> {
      new Response('http://front50', 200, 'OK', [], null)
    }
    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.copyOf([
      'notification.type': 'savepipeline',
      'application': 'orca',
      'pipeline.name': 'my pipeline'
    ])
  }

}
