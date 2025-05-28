/*
 * Copyright 2019 Pivotal, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import okhttp3.MediaType;
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

class GetPipelinesFromArtifactTaskSpec extends Specification {

  Front50Service front50Service = Mock()
  OortService oortService = Mock()
  ArtifactUtils artifactUtils = Mock()
  private static final ObjectMapper objectMapper = OrcaObjectMapper.newInstance()

  @Subject
  def task = new GetPipelinesFromArtifactTask(front50Service, oortService, objectMapper, artifactUtils)

  void 'extract pipelines JSON from artifact'() {
    when:
    def context = [
      pipelinesArtifactId: '123'
    ]
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", context))

    then:
    1 * artifactUtils.getBoundArtifactForStage(_, '123', _) >> Artifact.builder().type('http/file')
      .reference('url1').build()
    1 * oortService.fetchArtifact(_) >> Calls.response(ResponseBody.create(MediaType.parse("application/json"), pipelineJson))
    front50Service.getPipelines(_) >> { return Calls.response([]) }
    result.status == ExecutionStatus.SUCCEEDED
    final pipelinesToSave = ((List<Map>) result.context.get("pipelinesToSave"))
    pipelinesToSave.size() == 3
    pipelinesToSave.every { !it.containsKey("id") }
  }

  void 'extract pipelines JSON from artifact with existing pipeline'() {
    when:
    def context = [
      pipelinesArtifactId: '123'
    ]
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", context))

    then:
    1 * artifactUtils.getBoundArtifactForStage(_, '123', _) >> Artifact.builder().type('http/file')
      .reference('url1').build()
    1 * oortService.fetchArtifact(_) >> Calls.response(ResponseBody.create(MediaType.parse("application/json"),pipelineJson))
    front50Service.getPipelines("app1") >> { return Calls.response([]) }
    front50Service.getPipelines("app2") >> { return Calls.response( [
      [name: "just judging", id: "exitingPipelineId"]
    ]) }
    result.status == ExecutionStatus.SUCCEEDED
    final pipelinesToSave = ((List<Map>) result.context.get("pipelinesToSave"))
    pipelinesToSave.size() == 3
    pipelinesToSave.find { it.name == "just judging"}.containsKey("id")
    pipelinesToSave.findAll { !it.name == "just judging"}.every { !it.containsKey("id") }
  }

  void 'fail to extract pipelines JSON from artifact without bound artifact'() {
    when:
    def context = [
      pipelinesArtifactId: '123'
    ]
    def result = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever", context))

    then:
    1 * artifactUtils.getBoundArtifactForStage(_, '123', _) >> null
    IllegalArgumentException ex = thrown()
    ex.message == "No artifact could be bound to '123'"
  }

  private static final pipelineJson = '''
{
  "app1": [{
    "name": "just waiting",
    "description": "",
    "parameterConfig": [],
    "notifications": [],
    "triggers": [],
    "stages": [{
      "refId": "wait1",
      "requisiteStageRefIds": [],
      "type": "wait",
      "waitTime": "420"
    }],
    "expectedArtifacts": [],
    "keepWaitingPipelines": false,
    "limitConcurrent": true
  }],
  "app2": [{
      "name": "just judging",
      "description": "",
      "parameterConfig": [],
      "notifications": [],
      "triggers": [],
      "stages": [{
        "refId": "manualJudgment1",
        "requisiteStageRefIds": [],
        "instructions": "Judge me.",
        "judgmentInputs": [],
        "type": "manualJudgment"
      }],
      "expectedArtifacts": [],
      "keepWaitingPipelines": false,
      "limitConcurrent": true
    },
    {
      "name": "waiting then judging",
      "description": "",
      "parameterConfig": [],
      "notifications": [],
      "triggers": [],
      "stages": [{
          "refId": "wait1",
          "requisiteStageRefIds": [],
          "type": "wait",
          "waitTime": "420",
          "comments": "Wait before judging me."
        },
        {
          "refId": "manualJudgment2",
          "requisiteStageRefIds": [
            "wait1"
          ],
          "instructions": "Okay, Judge me now.",
          "judgmentInputs": [],
          "type": "manualJudgment"
        }
      ],
      "expectedArtifacts": [],
      "keepWaitingPipelines": false,
      "limitConcurrent": true
    }
  ]
}

'''

}
