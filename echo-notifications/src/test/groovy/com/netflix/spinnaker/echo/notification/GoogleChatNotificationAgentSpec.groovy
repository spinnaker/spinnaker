/*
 * Copyright 2016 Google, Inc.
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

import com.netflix.spinnaker.echo.googlechat.GoogleChatMessage
import com.netflix.spinnaker.echo.googlechat.GoogleChatService
import com.netflix.spinnaker.echo.api.events.Event
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

public class GoogleChatNotificationAgentSpec extends Specification {

  def googleChat = Mock(GoogleChatService)
  @Subject
  def agent = new GoogleChatNotificationAgent(googleChatService: googleChat, spinnakerUrl: 'http://spinnaker')

  @Unroll
  def "sends correct message for #status status"() {
    given:
    def actualMessage = new BlockingVariable<GoogleChatMessage>()
    googleChat.sendMessage(*_) >> { partialWebhookURL, message ->
      actualMessage.set(message)
    }

    when:
    agent.sendNotifications([address: webhookURL], application, event, [type: type, link: "link"], status)

    then:
    actualMessage.get().message ==~ expectedMessage

    where:
    status      || expectedMessage
    "completed" || /Whatever's .* pipeline has completed/
    "starting"  || /Whatever's .* pipeline is starting/
    "failed"    || /Whatever's .* pipeline has failed/

    webhookURL = "https://chat.googleapis.com/v1/spaces/spooky"
    application = "whatever"
    event = new Event(content: [execution: [id: "1", name: "foo-pipeline"]])
    type = "pipeline"
  }

  @Unroll
  def "appends custom message to #status message if present"() {
    given:
    def actualMessage = new BlockingVariable<GoogleChatMessage>()
    googleChat.sendMessage(*_) >> { webhookURL, message ->
      actualMessage.set(message)
    }

    when:
    agent.sendNotifications([address: webhookURL, message: message], application, event, [type: type, link: "link"], status)

    then:
    actualMessage.get().message ==~ expectedMessage

    where:
    status      || expectedMessage
    "completed" || /Whatever's .* pipeline has completed\n\nCustom completed message/
    "starting"  || /Whatever's .* pipeline is starting\n\nCustom starting message/
    "failed"    || /Whatever's .* pipeline has failed\n\nCustom failed message/

    webhookURL = "https://chat.googleapis.com/v1/spaces/spooky"
    application = "whatever"
    event = new Event(content: [execution: [id: "1", name: "foo-pipeline"]])
    type = "pipeline"
    message = ["completed", "starting", "failed"].collectEntries {
      [("$type.$it".toString()): [text: "Custom $it message"]]
    }
  }

  @Unroll
  def "sends entirely custom message if customMessage field is present, performing text replacement if needed"() {
    given:
    def actualMessage = new BlockingVariable<GoogleChatMessage>()
    googleChat.sendMessage(*_) >> { webhookURL, message ->
      actualMessage.set(message)
    }

    when:
    agent.sendNotifications([address: webhookURL], application, event, [type: type], "etc")

    then:
    actualMessage.get().message == expectedMessage

    where:
    customMessage        || expectedMessage
    "a b c"              || "a b c"
    "a {{executionId}}!" || "a 1!"
    "b <{{link}}|link>?" || "b <http://spinnaker/#/applications/whatever/executions/details/1|link>?"

    webhookURL = "https://chat.googleapis.com/v1/spaces/spooky"
    application = "whatever"
    event = new Event(content: [
      execution: [id: "1", name: "foo-pipeline"],
      context  : [customMessage: customMessage],
      name     : 'a stage'
    ])
    type = "stage"
  }
}
