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

package com.netflix.spinnaker.orca.front50.model

import com.netflix.spinnaker.orca.front50.model.ApplicationNotifications.Notification
import spock.lang.Specification

class ApplicationNotificationsSpec extends Specification {

  void "getPipelineNotifications extracts only pipeline notifications"() {
    given:
    def slackPipes = new Notification([
      when   : ["pipeline.started", "pipeline.failed"],
      type   : "slack",
      address: "spinnaker"
    ])
    def slackTasks = new Notification([
      when   : ["task.completed"],
      type   : "slack",
      address: "spinnaker-tasks"
    ])
    def emailTasks = new Notification([
      when: ["task.started"],
      type: "email"
    ])

    ApplicationNotifications applicationNotification = new ApplicationNotifications()
    applicationNotification.set("slack", [slackPipes, slackTasks])
    applicationNotification.set("email", [emailTasks])
    applicationNotification.set("somethingEmpty", [])

    when:
    def pipelineNotifications = applicationNotification.getPipelineNotifications()

    then:
    pipelineNotifications == [slackPipes]
  }
}
