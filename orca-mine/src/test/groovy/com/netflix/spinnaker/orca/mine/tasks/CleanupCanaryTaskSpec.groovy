/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Action.DISABLE
import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Action.TERMINATE
import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Health.UNHEALTHY

class CleanupCanaryTaskSpec extends Specification {
  def katoService = Mock(KatoService)
  def task = new CleanupCanaryTask(katoService: katoService)

  def "should only attempt to cleanup canary clusters if TERMINATE is enabled"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "Canary", "Canary", [
      canary: [
        health      : UNHEALTHY,
        canaryConfig: [
          actionsForUnhealthyCanary: actions.collect {
            new CleanupCanaryTask.CanaryAction(action: it)
          }
        ]
      ]
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
    katoInvocations * katoService.requestOperations(_, _) >> {
      rx.Observable.from(new TaskId("TaskId"))
    }

    where:
    actions              || katoInvocations || stageOutpus
    [DISABLE]            || 0               || [:]
    [DISABLE, TERMINATE] || 1               || ["kato.last.task.id": "TaskId"]
  }
}
