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
import com.netflix.spinnaker.echo.api.events.Event
import freemarker.template.Configuration
import freemarker.template.Template
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables

class EmailNotificationAgentSpec extends Specification {

  def mailService = Stub(EmailNotificationService)
  def configuration = Mock(Configuration)
  def template = Mock(Template)
  @Subject
  def agent = new EmailNotificationAgent(mailService: mailService, configuration: configuration)

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
    1 * configuration.getTemplate("email-template.ftl", "UTF-8") >> template
    1 * template.process(_, _)

    where:
    type       || expectedSubject
    "stage"    || "Stage foo-stage for whatever's foo-pipeline pipeline has completed successfully"
    "pipeline" || "whatever's foo-pipeline pipeline has completed successfully"

    application = "whatever"
    address = "whoever@netflix.com"
    status = "complete"
    pipelineName = "foo-pipeline"
    stageName = "foo-stage"
    event = new Event(content: [
      execution: [id: "1", name: "foo-pipeline"],
      name: "foo-stage"
    ])
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
    configuration.getTemplate(_, "UTF-8") >> template

    and:
    def context = new BlockingVariable<Map>()
    template.process(*_) >> { ctx, writer ->
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
    event = new Event(content: [name: "foo-stage", execution: [name: "foo-pipeline"]])
    message = ["complete", "starting", "failed"].collectEntries {
      [("$type.$it".toString()): [text: "custom $it text"]]
    }
  }

  def "favors custom subject and body"() {
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
      [type: "stage"],
      status
    )

    then:
    email.subject == customSubject
    email.text == "<p>A <strong>custom</strong> body</p>\n"

    and:
    0 * _

    where:
    customSubject = "A custom subject"
    customBody = "A **custom** body"
    application = "whatever"
    address = "whoever@netflix.com"
    status = "complete"
    pipelineName = "foo-pipeline"
    stageName = "foo-stage"
    event = new Event(content: [context: [customSubject: customSubject,
                                          customBody: customBody],
                                name: "foo-stage",
                                execution: [id: "abc", name: "foo-pipeline"]])
  }
}
