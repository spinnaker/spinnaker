/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.notifications

import com.netflix.spinnaker.orca.echo.EchoService
import org.springframework.beans.factory.annotation.Autowired

/**
 * A notification agent for {@code EchoService} to poll for particular type of events
 */
abstract class EchoPollingNotificationAgent extends AbstractPollingNotificationAgent {

  @Autowired
  EchoService echoService
  private long lastCheck = System.currentTimeMillis()

  EchoPollingNotificationAgent(List<NotificationHandler> notificationHandlers) {
    super(notificationHandlers)
  }

  @Override
  void run() {
    try {
      def response = echoService.getEvents(notificationType, lastCheck)
      lastCheck = System.currentTimeMillis()
      def maps = objectMapper.readValue(response.body.in().text, List)
      handleNotification maps
    } catch (e) {
      e.printStackTrace()
    }
  }
}
