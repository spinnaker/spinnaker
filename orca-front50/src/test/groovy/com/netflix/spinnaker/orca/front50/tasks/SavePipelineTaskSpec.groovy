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
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.PipelineModelMutator
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import groovy.json.JsonOutput
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

class SavePipelineTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  PipelineModelMutator mutator = Mock()

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  SavePipelineTask task = new SavePipelineTask(Optional.of(front50Service), Optional.of([mutator]), objectMapper)

  def "should run model mutators with correct context"() {
    given:
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      id: 'my id',
      stages: []
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes),
      application: 'orca'
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * mutator.supports(pipeline) >> true
    1 * mutator.mutate(pipeline)
    1 * front50Service.savePipeline(pipeline, _) >> {
      new Response('http://front50', 200, 'OK', [], null)
    }
    result.status == ExecutionStatus.SUCCEEDED
    result.context == ImmutableMap.copyOf([
      'notification.type': 'savepipeline',
      'application': 'orca',
      'pipeline.name': 'my pipeline',
      'pipeline.id': 'my id'
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
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])
    Integer expectedIndex = 14
    Integer receivedIndex
    Map<String, Object> existingPipeline = [
      index: expectedIndex,
      id: 'existing-pipeline-id',
    ]

    when:
    front50Service.getPipeline(_ as String) >> existingPipeline
    front50Service.savePipeline(_, _) >> { Map<String, Object> newPipeline, Boolean staleCheck ->
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
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])
    Map<String, Object> existingPipeline = [
      index: existingIndex,
      id: 'existing-pipeline-id',
    ]

    when:
    front50Service.getPipelines(_) >> [existingPipeline]
    front50Service.savePipeline(_, _) >> { Map<String, Object> newPipeline, Boolean staleCheck ->
      receivedIndex = newPipeline.get("index")
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    receivedIndex == newIndex
  }

  def "should update runAsUser"() {
    given:
    String actualRunAsUser
    def pipeline = [
        application: 'orca',
        name: 'pipeline-id',
        stages: [],
        roles: roles,
        triggers: [
            [
                type: 'cron',
                enabled: true,
                runAsUser: existingRunAsUser
            ]
        ]
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
        pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes)
    ])

    when:
    stage.getContext().put('pipeline.serviceAccount', serviceAccountFromSaveServiceAccountTask)
    front50Service.savePipeline(_, _) >> { Map<String, Object> newPipeline, Boolean staleCheck ->
      actualRunAsUser = newPipeline.get("triggers")[0]?.runAsUser
      new Response('http://front50', 200, 'OK', [], null)
    }
    task.execute(stage)

    then:
    actualRunAsUser == expectedRunAsUser

    where:
    existingRunAsUser                     | roles    | serviceAccountFromSaveServiceAccountTask | expectedRunAsUser
    null                                  | ['test'] | 'pipeline-id@managed-service-account'    | 'pipeline-id@managed-service-account' // should update runAsUser with service account in each trigger where it is not set
    'pipeline-id@managed-service-account' | []       | 'someAccount@someOrg'                    | 'pipeline-id@managed-service-account' // should not update runAsUser in a trigger if it is already set
    'pipeline-id@managed-service-account' | []       | 'pipeline-id@managed-service-account'    | null // should remove runAsUser in triggers if roles are empty
    'test@shared-managed-service-account' | ['test'] | 'pipeline-id@managed-service-account'    | 'pipeline-id@managed-service-account' // roles are set, update runAsUser
    'pipeline-id@managed-service-account' | ['test'] | 'test@shared-managed-service-account'    | 'test@shared-managed-service-account' // roles are set, update runAsUser
    'pipeline-id@managed-service-account' | ['test'] | 'pipeline-id@managed-service-account'    | 'pipeline-id@managed-service-account' // roles are set, preserve runAsUser
    'someAccount@someOrg'                 | ['test'] | 'pipeline-id@managed-service-account'    | 'someAccount@someOrg' // roles and non-managed svc acct, preserve runAsUser
    null                                  | []       | 'pipeline-id@managed-service-account'    | null // roles are empty, don't introduce a managed svc acct
    null                                  | ['test'] | 'test@shared-managed-service-account'    | 'test@shared-managed-service-account' // roles are set, set runAsUser
    null                                  | ['test'] | 'pipeline-id@managed-service-account'    | 'pipeline-id@managed-service-account' // roles are set, set runAsUser
  }

  def "should fail task when front 50 save call fails"() {
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
    front50Service.savePipeline(_, _) >> { Map<String, Object> newPipeline, Boolean staleCheck ->
      new Response('http://front50', 500, 'OK', [], null)
    }
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
  }

  def "should fail and continue task when front 50 save call fails and stage is iterating over pipelines"() {
    given:
    def pipeline = [
      application: 'orca',
      name: 'my pipeline',
      stages: []
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", [
      pipeline: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipeline).bytes),
      isSavingMultiplePipelines: true
    ])

    when:
    front50Service.getPipelines(_) >> []
    front50Service.savePipeline(_, _) >> { Map<String, Object> newPipeline, Boolean staleCheck ->
      new Response('http://front50', 500, 'OK', [], null)
    }
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.FAILED_CONTINUE
  }

  def "should save multiple pipelines"() {
    given:
    def pipelines = [
        [
            application: 'test_app1',
            name: 'pipeline1',
            id: "id1",
            index: 1
        ],
        [
            application: 'test_app1',
            name: 'pipeline2',
            id: "id2",
            index: 2
        ],
        [
            application: 'test_app2',
            name: 'pipeline1',
            id: "id3",
            index: 1
        ],
        [
            application: 'test_ap2',
            name: 'pipeline2',
            id: "id4",
            index: 2
        ]
    ]
    def stage = new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.ORCHESTRATION, "bulk_save_app"),
        "savePipeline",
        [
            application: "bulk_save_app",
            pipelines: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipelines).bytes),
            isBulkSavingPipelines: true
        ]
    )

    when:
    def result = task.execute(stage)

    then:
    1 * front50Service.savePipelines(pipelines, _) >> {
      new Response('http://front50',
          200,
          'OK',
          [],
          new TypedString(new JsonOutput().toJson(
              [
                  successful_pipelines_count: 4,
                  successful_pipelines: ["pipeline1", "pipeline2", "pipeline3", "pipeline4"],
                  failed_pipelines_count: 0,
                  failed_pipelines: []
              ]
          ))
      )
    }
    result == TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context([
            "notification.type": "savepipeline",
            application: "bulk_save_app",
            bulksave: [
                successful_pipelines_count: 4,
                successful_pipelines: ["pipeline1", "pipeline2", "pipeline3", "pipeline4"],
                failed_pipelines_count: 0,
                failed_pipelines: []
            ]])
        .build()
  }

  def "should fail save multiple pipelines if no pipelines provided"() {
    given:
    def stage = new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.ORCHESTRATION, "bulk_save_app"),
        "savePipeline",
        [
            application          : "bulk_save_app",
            isBulkSavingPipelines: true
        ]
    )

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalArgumentException)
    error.getMessage() == "pipelines context must be provided when saving multiple pipelines"
  }

  def "should fail task when front 50 save pipelines call fails"() {
    given:
    def pipelines = [
        [
            application: 'test_app1',
            name: 'pipeline1',
            id: "id1",
            index: 1
        ]
    ]
    def stage = new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.ORCHESTRATION, "bulk_save_app"),
        "savePipeline",
        [
            application          : "bulk_save_app",
            isBulkSavingPipelines: true,
            pipelines: Base64.encoder.encodeToString(objectMapper.writeValueAsString(pipelines).bytes)
        ]
    )

    when:
    def result = task.execute(stage)

    then:
    1 * front50Service.savePipelines(pipelines, _) >> {
      new Response('http://front50', 500, 'OK', [], null)
    }
    result.status == ExecutionStatus.TERMINAL
  }
}
