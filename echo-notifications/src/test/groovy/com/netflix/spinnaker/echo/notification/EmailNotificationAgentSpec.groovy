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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.email.EmailNotificationService
import com.netflix.spinnaker.echo.model.Event
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables

class EmailNotificationAgentSpec extends Specification {

  def mailService = Stub(EmailNotificationService)
  def engine = Mock(VelocityEngine)
  @Subject
  def agent = new EmailNotificationAgent(mailService: mailService, engine: engine)

  @Unroll
  def "subject is correct for a #type #status notification"() {
    given:
    def email = new BlockingVariables()
    mailService.send(*_) >> { to, cc, subject, text ->
      email.to = to
      email.cc = cc
      email.subject = subject
      email.text = text
    }

    when:
    agent.sendNotifications(
      [address: address],
      application,
      event,
      [type: type],
      status
    )

    then:
    email.subject == expectedSubject

    and:
    1 * engine.mergeTemplate("${type}.vm", _, _, _)

    where:
    type       || expectedSubject
    "stage"    || "Stage foo-stage for whatever's foo-pipeline pipeline has completed successfully"
    "pipeline" || "whatever's foo-pipeline pipeline has completed successfully"

    application = "whatever"
    address = "whoever@netflix.com"
    status = "complete"
    pipelineName = "foo-pipeline"
    stageName = "foo-stage"
    event = new Event(content: [context: [stageDetails: [name: "foo-stage"]], execution: [name: "foo-pipeline"]])
  }

  @Unroll
  def "custom text is appended to email body for #status notification"() {
    given:
    def email = new BlockingVariables()
    mailService.send(*_) >> { to, cc, subject, text ->
      email.to = to
      email.cc = cc
      email.subject = subject
      email.text = text
    }

    and:
    def context = new BlockingVariable<VelocityContext>()
    engine.mergeTemplate(*_) >> { template, encoding, ctx, writer ->
      context.set(ctx)
    }

    when:
    agent.sendNotifications(
      [address: address, message: message],
      application,
      event,
      [type: type],
      status
    )

    then:
    context.get().get("message") == message."$type.$status".text

    where:
    status     | _
    "complete" | _
    "starting" | _
    "failed"   | _

    type = "stage"
    application = "whatever"
    address = "whoever@netflix.com"
    pipelineName = "foo-pipeline"
    stageName = "foo-stage"
    event = new Event(content: [context: [stageDetails: [name: "foo-stage"]], execution: [name: "foo-pipeline"]])
    message = ["complete", "starting", "failed"].collectEntries {
      [("$type.$it".toString()): [text: "custom $it text"]]
    }
  }

}
