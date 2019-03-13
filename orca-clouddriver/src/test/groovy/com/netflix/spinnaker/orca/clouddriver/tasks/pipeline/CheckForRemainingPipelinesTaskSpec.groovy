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

class CheckForRemainingPipelinesTaskSpec extends Specification {

  @Subject
  final task = new CheckForRemainingPipelinesTask()

  void 'keep looping to save more tasks'() {
    when:
    def context = [
      pipelinesToSave: [
        [ name: "pipeline1" ]
      ]
    ]
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "whatever", context))

    then:
    result.status == ExecutionStatus.REDIRECT
  }

  void 'stop looping when there are no more more tasks to save'() {
    when:
    def context = [
      pipelinesToSave: [
      ]
    ]
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "whatever", context))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

}
