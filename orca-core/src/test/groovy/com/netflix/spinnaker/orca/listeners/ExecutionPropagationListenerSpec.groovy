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
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.*

class ExecutionPropagationListenerSpec extends Specification {
  def persister = Mock(Persister)
  def execution = Mock(Execution) {
    _ * getId() >> { return "1" }
  }

  @Unroll
  void "beforeExecution should mark as RUNNING"() {
    given:
    def listener = new ExecutionPropagationListener(isBeforeJobEnabled, false)

    when:
    listener.beforeExecution(persister, execution)

    then:
    invocations * persister.updateStatus("1", RUNNING)

    where:
    isBeforeJobEnabled || invocations
    true               || 1
    false              || 0
  }

  @Unroll
  void "afterExecution should update execution status"() {
    given:
    def listener = new ExecutionPropagationListener(false, isAfterJobEnabled)

    when:
    listener.afterExecution(persister, execution, sourceExecutionStatus, true)

    then:
    invocations * persister.updateStatus("1", expectedExecutionStatus)

    where:
    isAfterJobEnabled | sourceExecutionStatus || invocations || expectedExecutionStatus
    false             | null                  || 0           || null
    true              | SUCCEEDED             || 1           || SUCCEEDED
    true              | CANCELED              || 1           || CANCELED
    true              | STOPPED               || 1           || SUCCEEDED // treat STOPPED as a non-failure
    true              | null                  || 1           || TERMINAL  // if no source execution status can be derived, consider the execution TERMINAL
  }
}
