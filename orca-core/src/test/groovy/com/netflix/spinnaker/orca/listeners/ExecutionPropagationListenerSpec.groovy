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

  void "beforeExecution should mark as RUNNING"() {
    given:
    def listener = new ExecutionPropagationListener()

    when:
    listener.beforeExecution(persister, execution)

    then:
    1 * persister.updateStatus("1", RUNNING)
  }

  void "afterExecution should update execution status"() {
    given:
    def listener = new ExecutionPropagationListener()

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
  }
}
