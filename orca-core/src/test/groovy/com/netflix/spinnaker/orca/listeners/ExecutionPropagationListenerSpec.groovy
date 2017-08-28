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

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ExecutionPropagationListenerSpec extends Specification {

  @Subject
  def listener = new ExecutionPropagationListener()
  def persister = Mock(Persister)

  def "beforeExecution should mark as RUNNING"() {
    when:
    listener.beforeExecution(persister, execution)

    then:
    1 * persister.updateStatus(execution.id, RUNNING)

    where:
    execution = pipeline()
  }

  @Unroll
  def "afterExecution should update execution status to #expectedExecutionStatus if last stage is #sourceExecutionStatus"() {
    when:
    listener.afterExecution(persister, execution, sourceExecutionStatus, true)

    then:
    1 * persister.updateStatus(execution.id, expectedExecutionStatus)

    where:
    sourceExecutionStatus || expectedExecutionStatus
    SUCCEEDED             || SUCCEEDED
    CANCELED              || CANCELED
    STOPPED               || SUCCEEDED // treat STOPPED as a non-failure
    null                  || TERMINAL  // if no source execution status can be derived, consider the execution TERMINAL

    execution = pipeline()
  }

  def "afterExecution should update execution status to succeeded if all stages are skipped or succeeded"() {
    when:
    listener.afterExecution(persister, execution, STOPPED, true)

    then:
    1 * persister.updateStatus(execution.id, SUCCEEDED)

    where:
    execution = pipeline {
      stage {
        status = SUCCEEDED
      }
      stage {
        status = SKIPPED
        context = [completeOtherBranchesThenFail: true]
      }
    }
  }

  @Unroll
  def "pipeline status is #expectedExecutionStatus if an earlier failed stage was set to #description the pipeline on failure"() {
    when:
    listener.afterExecution(persister, execution, SUCCEEDED, branchStatus == SUCCEEDED)

    then:
    1 * persister.updateStatus(execution.id, expectedExecutionStatus)

    where:
    branchStatus | completeOtherBranchesThenFail || expectedExecutionStatus
    STOPPED      | true                          || TERMINAL
    STOPPED      | false                         || SUCCEEDED

    description = completeOtherBranchesThenFail ? "fail" : "pass"

    execution = pipeline {
      stage {
        type = "one"
        status = branchStatus
        context.completeOtherBranchesThenFail = completeOtherBranchesThenFail
      }
      stage {
        type = "two"
        status = SUCCEEDED
      }
    }
  }
}
