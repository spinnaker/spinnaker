/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import spock.lang.Subject

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class CompositeExecutionListenerSpec extends Specification {
  def listener1 = Mock(ExecutionListener)
  def listener2 = Mock(ExecutionListener)

  @Subject
  def composite = new CompositeExecutionListener(listener1, listener2)

  def persister = Stub(Persister)
  def execution = Stub(Execution)

  def "delegates beforeExecution to wrapped listeners in forward order"() {
    when:
    composite.beforeExecution(persister, execution)

    then:
    1 * listener1.beforeExecution(*_)

    then:
    1 * listener2.beforeExecution(*_)
  }

  def "delegates afterExecution to wrapped listeners in reverse order"() {
    when:
    composite.afterExecution(persister, execution, SUCCEEDED, true)

    then:
    1 * listener2.afterExecution(*_)

    then:
    1 * listener1.afterExecution(*_)
  }
}
