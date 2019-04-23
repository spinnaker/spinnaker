/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.echo.pubsub

import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription
import spock.lang.Specification
import spock.lang.Subject

class GoogleCloudBuildEventCreatorSpec extends Specification {
  final String ACCOUNT_NAME = "my-account"
  final String BUILD_ID = "1a9ea355-eb3d-4148-b81b-875d07ea118b"
  final String BUILD_STATUS = "QUEUED"
  final String PAYLOAD = "test payload"

  @Subject
  GoogleCloudBuildEventCreator eventCreator = new GoogleCloudBuildEventCreator()

  def "create a googleCloudBuild event"() {
    given:
    MessageDescription messageDescription = MessageDescription.builder()
      .subscriptionName(ACCOUNT_NAME)
      .messagePayload(PAYLOAD)
      .messageAttributes([
        buildId: BUILD_ID,
        status: BUILD_STATUS
      ]).build()

    when:
    Event event = eventCreator.createEvent(messageDescription)

    then:
    event.getDetails().getType() == "googleCloudBuild"
    event.getContent().get("messageDescription") == messageDescription
  }
}
