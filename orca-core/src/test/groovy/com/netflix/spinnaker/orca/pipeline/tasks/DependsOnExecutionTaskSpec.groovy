/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.orchestration
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DependsOnExecutionTaskSpec extends Specification {

  def repository = Mock(ExecutionRepository)
  def task = new DependsOnExecutionTask(repository)

  @Shared
  def passed = orchestration {
    status = ExecutionStatus.SUCCEEDED
  }

  @Shared
  def running = orchestration {
    status = ExecutionStatus.RUNNING
  }

  @Shared
  def failed = orchestration {
    status = ExecutionStatus.TERMINAL
  }

  @Unroll
  void "passes if the dependency passes"() {
    given:
    def stage = getStage(execution.id)

    when:
    1 * repository.retrieve(ORCHESTRATION, execution.id) >> execution
    def result = task.execute(stage)

    then:
    result.status == expectedTaskStatus

    where:
    execution || expectedTaskStatus
    passed    || ExecutionStatus.SUCCEEDED
    running   || ExecutionStatus.RUNNING
    failed    || ExecutionStatus.CANCELED
  }

  Stage getStage(String dependencyId) {
    return stage {
      refId = "1"
      type = "dependsOnExecution"
      context["executionType"] = ORCHESTRATION
      context["executionId"] = dependencyId
    }
  }
}
