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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.MDC
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class CompoundExecutionOperatorSpec extends Specification {
  ExecutionRepository repository = Mock(ExecutionRepository)
  ExecutionRunner runner = Mock(ExecutionRunner)
  def execution = Mock(Execution)

  def setupSpec() {
    MDC.clear()
  }

  @Subject
  CompoundExecutionOperator operator = new CompoundExecutionOperator(repository, runner, new RetrySupport())

  def 'should not push messages on the queue for foreign executions'() {
    when:
    operator.cancel(PIPELINE, "id")

    then: 'we never call runner.cancel(), only repository.cancel()'
    1 * repository.retrieve(PIPELINE, "id") >> execution
    _ * execution.getPartition() >> "foreign"
    1 * repository.handlesPartition("foreign") >> false
    1 * repository.cancel(PIPELINE, "id", "anonymous", null)
    0 * _
  }

  def 'should handle both the queue and the execution repository for local executions'() {
    when:
    operator.cancel(PIPELINE, "id")

    then: 'we call both runner.cancel() and repository.cancel()'
    1 * repository.retrieve(PIPELINE, "id") >> execution
    _ * execution.getPartition() >> "local"
    1 * repository.handlesPartition("local") >> true
    1 * repository.cancel(PIPELINE, "id", "anonymous", null)
    1 * runner.cancel(execution, "anonymous", null)
  }
}
