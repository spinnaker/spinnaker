/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import spock.lang.Specification
import spock.lang.Subject

class WaitTaskSpec extends Specification {
  def timeProvider = new WaitTask.TimeProvider()
  @Subject task = new WaitTask(timeProvider: timeProvider)

  void "should wait for a configured period"() {
    setup:
      def wait = 5
    def pipeline = Execution.newPipeline("orca")
      def stage = new Stage(pipeline, "wait", [waitTime: wait])

    when:
      def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.RUNNING
      result.context.waitTaskState.startTime > 1
      stage.context.putAll(result.context)

    when:
      timeProvider.millis = System.currentTimeMillis() + 10000

    and:
      result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED
      result.context.waitTaskState.size() == 0

  }

  void "should skip waiting when marked in context"() {
    setup:
    def pipeline = Execution.newPipeline("orca")
    def stage = new Stage(pipeline, "wait", [waitTime: 1000000])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    stage.context.putAll(result.context)

    when:
    timeProvider.millis = System.currentTimeMillis() + 10000
    stage.context.skipRemainingWait = true

    and:
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

  }
}
