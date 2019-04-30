/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.echo.tasks

import com.fasterxml.jackson.databind.ObjectMapper

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

@Slf4j
@Component
@CompileStatic
class PageApplicationOwnerTask implements RetryableTask {
  long backoffPeriod = 15000
  long timeout = TimeUnit.MINUTES.toMillis(5)

  @Autowired
  EchoService echoService

  @Autowired(required = false)
  Front50Service front50Service

  @Override
  TaskResult execute(Stage stage) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to fetch pager duty. Fix this by setting front50.enabled: true");
    }
    def applications = stage.context.applications as List<String>
    def incomingPagerDutyKeys = stage.context.keys as List<String>
    def allPagerDutyKeys = []

    if (incomingPagerDutyKeys) {
      // Add all arbitrary keys to the pager duty key list
      allPagerDutyKeys.addAll(incomingPagerDutyKeys)
    }

    // Add applications to the pager duty key list if they have a valid key
    if (applications) {
      def invalidApplications = []
      applications.each { application ->
        def applicationPagerDutyKey = fetchApplicationPagerDutyKey(application)
        if (!applicationPagerDutyKey) {
          invalidApplications.push(application)
        } else {
          allPagerDutyKeys.push(applicationPagerDutyKey)
        }
      }

      if (invalidApplications.size() > 0) {
        throw new IllegalStateException("${invalidApplications.join(", ")} ${invalidApplications.size() > 1 ? "do" : "does"} not have a pager duty service key.")
      }
    }

    def details = stage.context.details
    if (details == null) {
      details = [:]
    }
    if (details["from"] == null) {
      details["from"] = stage.execution.authentication.user
    }

    // echo service will not send a proper response back if there is an error, it will just throw an exception
    echoService.create(
      new EchoService.Notification(
        to: allPagerDutyKeys,
        notificationType: EchoService.Notification.Type.PAGER_DUTY,
        source: new EchoService.Notification.Source(user: stage.execution.authentication.user),
        additionalContext: [
          message: stage.context.message,
          details: details
        ]
      )
    )

    log.info("Sent page (key(s): ${allPagerDutyKeys.join(", ")}, message: '${stage.context.message}')")
    return TaskResult.ofStatus(SUCCEEDED)
  }

  private String fetchApplicationPagerDutyKey(String applicationName) {
    try {
      def application = front50Service.get(applicationName)
      return application.details().pdApiKey as String
    } catch (Exception e) {
      log.error("Unable to retrieve application '${applicationName}', reason: ${e.message}")
      return null
    }
  }
}
