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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class PreparePipelineToSaveTaskSpec extends Specification {

  final ObjectMapper objectMapper = OrcaObjectMapper.newInstance()

  @Subject
  final task = new PreparePipelineToSaveTask(objectMapper)

  void 'prepare pipeline for save pipeline task'() {
    when:
    def context = [
      pipelinesToSave: [
        [ name: "pipeline1" ]
      ]
    ]
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "whatever", context))

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.get("pipeline") == "eyJuYW1lIjoicGlwZWxpbmUxIn0="
    result.context.get("pipelinesToSave") == []
  }

  void 'prepare pipeline for save pipeline task with multiple pipelines'() {
    when:
    def context = [
      pipelinesToSave: [
        [ name: "pipeline1" ],
        [ name: "pipeline2" ]
      ]
    ]
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "whatever", context))

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.get("pipeline") == "eyJuYW1lIjoicGlwZWxpbmUxIn0="
    result.context.get("pipelinesToSave") == [ [name: "pipeline2"] ]
  }

  void 'fail to prepare pipeline for save pipeline task with no pipelines'() {
    when:
    def context = [:]
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "whatever", context))

    then:
    result.status == ExecutionStatus.TERMINAL
  }

}
