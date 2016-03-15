/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners

import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class CompositeStageListenerSpec extends Specification {

  def listener1 = Mock(StageListener)
  def listener2 = Mock(StageListener)
  @Subject def composite = new CompositeStageListener(listener1, listener2)

  def persister = Stub(Persister)
  def stage = Stub(Stage)
  def task = Stub(Task)

  def "delegates beforeTask to wrapped listeners in forward order"() {
    when:
    composite.beforeTask(persister, stage, task)

    then:
    1 * listener1.beforeTask(*_)

    then:
    1 * listener2.beforeTask(*_)
  }

  def "delegates beforeStage to wrapped listeners in forward order"() {
    when:
    composite.beforeStage(persister, stage)

    then:
    1 * listener1.beforeStage(*_)

    then:
    1 * listener2.beforeStage(*_)
  }

  def "delegates afterTask to wrapped listeners in reverse order"() {
    when:
    composite.afterTask(persister, stage, task, SUCCEEDED, true)

    then:
    1 * listener2.afterTask(*_)

    then:
    1 * listener1.afterTask(*_)
  }

  def "delegates afterStage to wrapped listeners in reverse order"() {
    when:
    composite.afterStage(persister, stage)

    then:
    1 * listener2.afterStage(*_)

    then:
    1 * listener1.afterStage(*_)
  }

}
