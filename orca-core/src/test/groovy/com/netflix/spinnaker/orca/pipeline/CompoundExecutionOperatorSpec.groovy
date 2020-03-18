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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.MDC
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.function.Consumer

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

class CompoundExecutionOperatorSpec extends Specification {
  ExecutionRepository repository = Mock(ExecutionRepository)
  ExecutionRunner runner = Mock(ExecutionRunner)
  def execution = Mock(PipelineExecution)
  def stage = Mock(StageExecution)

  def setupSpec() {
    MDC.clear()
  }

  @Subject
  CompoundExecutionOperator operator = new CompoundExecutionOperator(repository, runner, new RetrySupport())

  @Unroll
  def '#method call should not push messages on the queue for foreign executions'() {
    when:
    operator."$method"(*args)

    then: 'we never call runner.$method(), only repository.$method()'
    _ * execution.getPartition() >> "foreign"
    _ * execution.stageById('stageId') >> stage
    _ * stage.getId() >> "stageId"

    _ * repository.retrieve(PIPELINE, "id") >> execution
    1 * repository.handlesPartition("foreign") >> false
    1 * repository."$repoMethod"(*_)
    0 * runner._(*_)

    where:
    method        | repoMethod   | args
    'cancel'      | 'cancel'     | [PIPELINE, "id"]
    'pause'       | 'pause'      | [PIPELINE, "id", "user"]
    'resume'      | 'resume'     | [PIPELINE, "id", "user", false]
    'updateStage' | 'storeStage' | [PIPELINE, "id", "stageId", {} ]
  }

  @Unroll
  def 'should not push messages on the queue if the repository action fails'() {
    given:
    def runnerAction = Mock(Consumer)

    when:
    def returnedExecution = operator.doInternal(
        runnerAction,
        { throw new RuntimeException("repository action is failing") },
        "faily action",
        PIPELINE,
        "id"
    )

    then: 'we never call the runner action'
    _ * repository.retrieve(PIPELINE, "id") >> execution
    _ * execution.getPartition() >> "local"
    _ * repository.handlesPartition("local") >> true

    returnedExecution == null
    0 * runnerAction.accept(execution)
  }

  @Unroll
  def '#method call should handle both the queue and the execution repository for local executions'() {
    when:
    operator."$method"(*args)

    then: 'we call both runner.cancel() and repository.cancel()'
    1 * repository.retrieve(PIPELINE, "id") >> execution
    _ * execution.getPartition() >> "local"
    1 * repository.handlesPartition("local") >> true
    1 * repository."$method"(*_)
    1 * runner."$runnerMethod"(*_)

    where:
    method   | runnerMethod | args
    'cancel' | 'cancel'     | [PIPELINE, "id"]
    'pause'  | 'reschedule' | [PIPELINE, "id", "user"]
    'resume' | 'unpause'    | [PIPELINE, "id", "user", false]
  }

  def 'updateStage() updates the stage'() {
    given:
    StageExecutionImpl stage = new StageExecutionImpl(id: 'stageId')

    when:
    operator.updateStage(PIPELINE, 'id', 'stageId',
        { it.setLastModified(new StageExecution.LastModifiedDetails(user: 'user')) })

    then:
    _ * repository.retrieve(PIPELINE, 'id') >> execution
    1 * execution.stageById('stageId') >> stage
    1 * repository.storeStage(stage)
    stage.getLastModified().getUser() == 'user'
  }
}
