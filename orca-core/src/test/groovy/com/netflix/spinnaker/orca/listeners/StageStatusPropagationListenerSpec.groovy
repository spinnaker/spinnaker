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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class StageStatusPropagationListenerSpec extends Specification {
  def persister = Mock(Persister)

  @Unroll
  void "beforeTask #action as RUNNING when stage status is #stageStatus"() {
    given:
    def listener = new StageStatusPropagationListener()
    def stage = new Stage(Execution.newPipeline("orca"), "test")
    def task = new Task()

    and:
    stage.status = stageStatus

    when:
    listener.beforeTask(persister, stage, task)

    then:
    invocations * persister.save({ s ->
      s.startTime != null
      s.status == RUNNING
    } as Stage)

    where:
    stageStatus || invocations
    NOT_STARTED || 1
    RUNNING     || 1
    SUCCEEDED   || 0
    STOPPED     || 0

    action = stageStatus.complete ? "should mark" : "should not mark"
  }

  @Unroll
  void "afterTask should update stage status"() {
    given:
    if (!tasks.empty) {
      tasks.first().stageStart = true
      tasks.last().stageEnd = true
    }

    and:
    def listener = new StageStatusPropagationListener()
    def stage = new Stage(Execution.newPipeline("orca"), "test")
    stage.tasks = tasks

    when:
    listener.afterTask(persister, stage, task, sourceExecutionStatus, true)

    then:
    1 * persister.save({ s ->
      s.status == expectedStageStatus
    } as Stage)

    where:
    tasks                                          | sourceExecutionStatus || expectedStageStatus
    []                                             | null                  || TERMINAL
    [t("1", RUNNING)]                              | RUNNING               || RUNNING
    [t("1", SUCCEEDED)]                            | SUCCEEDED             || SUCCEEDED
    [t("1", SUCCEEDED), t("2", NOT_STARTED)]       | SUCCEEDED             || RUNNING
    [t("1", FAILED_CONTINUE), t("2", NOT_STARTED)] | SUCCEEDED             || FAILED_CONTINUE

    task = tasks.empty ? null : tasks.first()
  }

  private Task t(String name, ExecutionStatus status) {
    return new Task(name: name, status: status)
  }
}
