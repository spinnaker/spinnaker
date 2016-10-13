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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.*

class StageTaskPropagationListenerSpec extends Specification {
  def persister = Mock(Persister)

  @Unroll
  void "before should update task status to RUNNING if not already started"() {
    given:
    def listener = new StageTaskPropagationListener()
    def task = new DefaultTask(startTime: startTime)
    def stage = new PipelineStage(new Pipeline(), "test")
    stage.tasks = [task]

    when:
    listener.beforeTask(persister, stage, task)

    then:
    invocations * persister.save({ s ->
      s.tasks[0].status == RUNNING
      s.tasks[0].startTime != null
      s.tasks[0].endTime == null
    } as Stage)

    where:
    startTime || invocations
    null      || 1
    1234567    | 0
  }

  @Unroll
  void "afterTask should update task status to #sourceExecutionStatus"() {
    given:
    def listener = new StageTaskPropagationListener()
    def task = new DefaultTask()
    def stage = new PipelineStage(new Pipeline(), "test")
    stage.tasks = [task]

    when:
    listener.afterTask(persister, stage, task, sourceExecutionStatus, true)

    then:
    1 * persister.save({ s ->
      s.tasks[0].status == sourceExecutionStatus
      s.tasks[0].endTime != null
    } as Stage)

    where:
    sourceExecutionStatus << ExecutionStatus.values()
  }
}
