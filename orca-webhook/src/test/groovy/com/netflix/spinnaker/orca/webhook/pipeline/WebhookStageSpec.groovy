/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.webhook.pipeline

import com.netflix.spinnaker.kork.exceptions.UserException
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import com.netflix.spinnaker.orca.webhook.tasks.CreateWebhookTask
import com.netflix.spinnaker.orca.webhook.tasks.MonitorWebhookTask
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WebhookStageSpec extends Specification {

  def builder = Mock(TaskNode.Builder)

  @Subject
  webhookStage = new WebhookStage()

  @Unroll
  def "Should create correct tasks"() {
    given:
    def stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline("orca"),
      "webhook",
      [
        waitForCompletion: waitForCompletion,
        waitBeforeMonitor: waitTime,
        monitorOnly: monitorOnly
      ])

    when:
    webhookStage.taskGraph(stage, builder)

    then:
    expectedCreateTaskCount * builder.withTask("createWebhook", CreateWebhookTask)
    expectedWaitTaskCount * builder.withTask("waitBeforeMonitorWebhook", WaitTask)
    expectedMonitorTaskCount * builder.withTask("monitorWebhook", MonitorWebhookTask)

    stage.context.waitTime == expectedWaitTimeInContext

    where:
    waitForCompletion | monitorOnly | waitTime || expectedWaitTimeInContext | expectedWaitTaskCount | expectedMonitorTaskCount | expectedCreateTaskCount
    true              | false       | 10       || 10                        | 1                     | 1                        | 1
    true              | null        | "2"      || 2                         | 1                     | 1                        | 1
    "true"            | "false"     | 0        || null                      | 0                     | 1                        | 1
    true              | false       | -1       || null                      | 0                     | 1                        | 1
    false             | false       | 10       || null                      | 0                     | 0                        | 1
    false             | false       | 0        || null                      | 0                     | 0                        | 1
    true              | true        | 10       || 10                        | 1                     | 1                        | 0
    true              | "true"      | "2"      || 2                         | 1                     | 1                        | 0
    "true"            | true        | 0        || null                      | 0                     | 1                        | 0
    true              | "true"      | -1       || null                      | 0                     | 1                        | 0
  }

  def "Should throw on invalid input"() {
    given:
    def stage = new StageExecutionImpl(
        PipelineExecutionImpl.newPipeline("orca"),
        "webhook",
        [
            waitForCompletion: false,
            monitorOnly: true
        ])

    when:
    webhookStage.taskGraph(stage, builder)

    then:
    thrown(UserException)
  }
}
