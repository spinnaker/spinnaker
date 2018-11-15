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

  def "should copy existing pipeline index to new pipeline"() {
    given:
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      stages: [],
      id: 'existing-pipeline-id'
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])
    Integer expectedIndex = 14
    Integer receivedIndex
    Map<String, Object> existingPipeline = [
      index: expectedIndex,
      id: 'existing-pipeline-id',
    ]

    when:
    front50Service.getPipelines(_) >> [existingPipeline]
    front50Service.savePipeline(_) >> { Map<String, Object> newPipeline ->
      receivedIndex = newPipeline.get("index")
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    receivedIndex == expectedIndex
  }

  def "should not copy existing pipeline index to new pipeline with own index"() {
    given:
    Integer existingIndex = 14
    Integer newIndex = 4
    Integer receivedIndex
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      stages: [],
      id: 'existing-pipeline-id',
      index: newIndex
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])
    Map<String, Object> existingPipeline = [
      index: existingIndex,
      id: 'existing-pipeline-id',
    ]

    when:
    front50Service.getPipelines(_) >> [existingPipeline]
    front50Service.savePipeline(_) >> { Map<String, Object> newPipeline ->
      receivedIndex = newPipeline.get("index")
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    receivedIndex == newIndex
  }

  def "should update runAsUser with service account in each trigger where it is not set "() {
    given:
    String runAsUser
    def expectedRunAsUser = 'my-pipeline-id@managed-service-account'
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      roles: ['foo'],
      stages: [],
      triggers: [
        [
          type: 'cron',
          enabled: true
        ]
      ]
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    stage.getContext().put('pipeline.serviceAccount', expectedRunAsUser)
    front50Service.savePipeline(_) >> { Map<String, Object> newPipeline ->
      runAsUser = newPipeline.triggers[0].runAsUser
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    runAsUser == expectedRunAsUser
  }

  def "should not update runAsUser in a trigger if it is already set"() {
    given:
    String runAsUser
    def expectedRunAsUser = 'someServiceAccount'
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      stages: [],
      triggers: [
        [
          type: 'cron',
          enabled: true,
          runAsUser: expectedRunAsUser
        ]
      ]
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    stage.getContext().put('pipeline.serviceAccount', 'my-pipeline@managed-service-account')
    front50Service.savePipeline(_) >> { Map<String, Object> newPipeline ->
      runAsUser = newPipeline.get("triggers")[0]?.runAsUser
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    runAsUser == expectedRunAsUser
  }

  def "should remove runAsUser in triggers if roles are empty"(){
    given:
    String runAsUser
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      roles: [],
      stages: [],
      triggers: [
        [
          type: 'cron',
          enabled: true,
          runAsUser: 'id@managed-service-account'
        ]
      ]
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    stage.getContext().put('pipeline.serviceAccount', 'id@managed-service-account')
    front50Service.savePipeline(_) >> { Map<String, Object> newPipeline ->
      runAsUser = newPipeline.get("triggers")[0]?.runAsUser
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    runAsUser == null
  }
}
