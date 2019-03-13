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
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification
import spock.lang.Subject

class CheckPipelineResultsTaskSpec extends Specification {

  final ObjectMapper objectMapper = OrcaObjectMapper.newInstance()

  @Subject
  final task = new CheckPipelineResultsTask(objectMapper)

  void 'add created pipeline success to context'() {
    when:
    def context = [
      application: 'app1',
      'pipeline.name': 'pipeline1'
    ]
    final Task savePipelineTask = new Task().with {
      setName('savePipeline')
      setStatus(ExecutionStatus.SUCCEEDED)
      return it
    }
    final Stage stage = new Stage(Execution.newPipeline("orca"), "whatever", context).with {
      setTasks([savePipelineTask])
      return it
    }
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.get("pipelinesCreated") == [[application: 'app1', name: 'pipeline1']]
    result.context.get("pipelinesUpdated") == []
    result.context.get("pipelinesFailedToSave") == []
  }

  void 'add updated pipeline success to context'() {
    when:
    def context = [
      application: 'app1',
      'pipeline.name': 'pipeline1',
      'isExistingPipeline': true
    ]
    final Task savePipelineTask = new Task().with {
      setName('savePipeline')
      setStatus(ExecutionStatus.SUCCEEDED)
      return it
    }
    final Stage stage = new Stage(Execution.newPipeline("orca"), "whatever", context).with {
      setTasks([savePipelineTask])
      return it
    }
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.get("pipelinesCreated") == []
    result.context.get("pipelinesUpdated") == [[application: 'app1', name: 'pipeline1']]
    result.context.get("pipelinesFailedToSave") == []
  }

  void 'add saved pipeline failure to context'() {
    when:
    def context = [
      application: 'app1',
      'pipeline.name': 'pipeline1'
    ]
    final Task savePipelineTask = new Task().with {
      setName('savePipeline')
      setStatus(ExecutionStatus.TERMINAL)
      return it
    }
    final Stage stage = new Stage(Execution.newPipeline("orca"), "whatever", context).with {
      setTasks([savePipelineTask])
      return it
    }
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.get("pipelinesCreated") == []
    result.context.get("pipelinesUpdated") == []
    result.context.get("pipelinesFailedToSave") == [[application: 'app1', name: 'pipeline1']]
  }

}
