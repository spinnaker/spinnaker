/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService
import com.netflix.spinnaker.echo.services.Front50Service
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

import static com.netflix.spinnaker.echo.config.DryRunConfig.DryRunProperties

class DryRunNotificationAgentSpec extends Specification {

  def front50 = Mock(Front50Service)
  def orca = Mock(OrcaService)
  def properties = new DryRunProperties()
  @Subject def agent = new DryRunNotificationAgent(front50, orca, properties)

  @Unroll
  def "ignores #type:#status notifications"() {
    when:
    agent.processEvent(event)

    then:
    0 * orca._

    where:
    type       | status     | execStatus
    "pipeline" | "starting" | "RUNNING"
    "pipeline" | "failed"   | "TERMINAL"
    "stage"    | "starting" | "RUNNING"
    "stage"    | "complete" | "RUNNING"

    event = new Event(
      details: [
        type       : "orca:$type:$status",
        application: "covfefe"
      ],
      content: [
        execution: [
          name         : "a-pipeline",
          notifications: [
            [
              type: "dryrun",
              when: ["pipeline.complete"]
            ]
          ],
          status       : execStatus
        ]
      ]
    )
  }

  def "triggers a pipeline run for a pipeline:complete notification"() {
    given:
    front50.getPipelines(application) >> Calls.response([pipeline])

    and:
    def captor = new BlockingVariable<Pipeline>(5)
    orca.trigger(_) >> { captor.set(it[0])
      Calls.response(null)
    }

    when:
    agent.processEvent(event)

    then:
    with(captor.get()) {
      name == "${pipeline.name} (dry run)"
      trigger.type == "dryrun"
      trigger.lastSuccessfulExecution == event.content.execution
    }

    where:
    pipelineConfigId = "1"
    application = "covfefe"
    pipeline = new Pipeline.PipelineBuilder()
      .application(application)
      .name("a-pipeline")
      .id(pipelineConfigId)
      .build()
    event = new Event(
      details: [
        type       : "orca:pipeline:complete",
        application: application
      ],
      content: [
        execution: [
          name            : pipeline.name,
          notifications   : [
            [
              type: "dryrun",
              when: ["pipeline.complete"]
            ]
          ],
          pipelineConfigId: pipelineConfigId,
          status          : "SUCCEEDED"
        ]
      ]
    )
  }

  def "adds notifications to triggered pipeline"() {
    given:
    front50.getPipelines(application) >> Calls.response([pipeline])

    and:
    properties.notifications = [
      [
        type   : "slack",
        address: "#a-slack-channel",
        level  : "pipeline",
        when   : ["pipeline.failed"]
      ]
    ]

    and:
    def captor = new BlockingVariable<Pipeline>(5)
    orca.trigger(_) >> { captor.set(it[0])
      Calls.response(null)
    }

    when:
    agent.processEvent(event)

    then:
    with(captor.get()) {
      notifications == properties.notifications
    }

    where:
    pipelineConfigId = "1"
    application = "covfefe"
    pipeline = new Pipeline.PipelineBuilder()
      .application(application)
      .name("a-pipeline")
      .id(pipelineConfigId)
      .build()
    event = new Event(
      details: [
        type       : "orca:pipeline:complete",
        application: application
      ],
      content: [
        execution: [
          name            : pipeline.name,
          notifications   : [
            [
              type: "dryrun",
              when: ["pipeline.complete"]
            ]
          ],
          pipelineConfigId: pipelineConfigId,
          status          : "SUCCEEDED"
        ]
      ]
    )
  }
}
