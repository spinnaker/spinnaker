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

import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
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
    def stage = new Stage(
      Execution.newPipeline("orca"),
      "webhook",
      [
        waitForCompletion: waitForCompletion,
        waitBeforeMonitor: waitTime
      ])

    when:
    webhookStage.taskGraph(stage, builder)

    then:
    1 * builder.withTask("createWebhook", CreateWebhookTask)

    then:
    expectedWaitTaskCount * builder.withTask("waitBeforeMonitorWebhook", WaitTask)

    then:
    expectedMonitorTaskCount * builder.withTask("monitorWebhook", MonitorWebhookTask)

    stage.context.waitTime == expectedWaitTimeInContext

    where:
    waitForCompletion | waitTime || expectedWaitTimeInContext | expectedWaitTaskCount | expectedMonitorTaskCount
    true              | 10       || 10                        | 1                     | 1
    true              | "2"      || 2                         | 1                     | 1
    "true"            | 0        || null                      | 0                     | 1
    true              | -1       || null                      | 0                     | 1
    false             | 10       || null                      | 0                     | 0
    false             | 0        || null                      | 0                     | 0
  }

}
