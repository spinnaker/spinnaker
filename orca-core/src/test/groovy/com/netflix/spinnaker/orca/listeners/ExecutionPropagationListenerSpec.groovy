/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.listeners

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class ExecutionPropagationListenerSpec extends Specification {

  @Subject
  def listener = new ExecutionPropagationListener()
  def persister = Mock(Persister)

  def "beforeExecution should mark as RUNNING"() {
    when:
    listener.beforeExecution(persister, execution)

    then:
    1 * persister.updateStatus("1", RUNNING)

    where:
    execution = Pipeline.builder().withId("1").build()
  }

  @Unroll
  def "afterExecution should update execution status to #expectedExecutionStatus if last stage is #sourceExecutionStatus"() {
    when:
    listener.afterExecution(persister, execution, sourceExecutionStatus, true)

    then:
    1 * persister.updateStatus("1", expectedExecutionStatus)

    where:
    sourceExecutionStatus || expectedExecutionStatus
    SUCCEEDED             || SUCCEEDED
    CANCELED              || CANCELED
    STOPPED               || SUCCEEDED // treat STOPPED as a non-failure
    null                  || TERMINAL  // if no source execution status can be derived, consider the execution TERMINAL

    execution = Pipeline.builder().withId("1").build()
  }

  def "afterExecution should update execution status to succeeded if all stages are skipped or succeeded"() {
    when:

    def stages = [[:], [completeOtherBranchesThenFail: true]]
    Execution execution = Pipeline.builder().withId("1").withStages(stages).build()
    execution.stages[0].status = SUCCEEDED
    execution.stages[1].status = SKIPPED
    listener.afterExecution(persister, execution, STOPPED, true)

    then:
    1 * persister.updateStatus("1", SUCCEEDED)

  }

  @Unroll
  def "pipeline status is #expectedExecutionStatus if an earlier failed stage was set to #description the pipeline on failure"() {
    given:
    execution.namedStage("one").with {
      status = branchStatus
      context.completeOtherBranchesThenFail = completeOtherBranchesThenFail
    }
    execution.namedStage("two").status = SUCCEEDED

    when:
    listener.afterExecution(persister, execution, SUCCEEDED, branchStatus == SUCCEEDED)

    then:
    1 * persister.updateStatus(execution.id, expectedExecutionStatus)

    where:
    branchStatus | completeOtherBranchesThenFail || expectedExecutionStatus
    STOPPED      | true                          || TERMINAL
    STOPPED      | false                         || SUCCEEDED

    description = completeOtherBranchesThenFail ? "fail" : "pass"

    execution = Pipeline
      .builder()
      .withId("1")
      .withStages("one", "two")
      .build()
  }
}
