/*
 * Copyright 2014 Netflix, Inc.
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
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import javax.annotation.PostConstruct
import groovy.util.logging.Slf4j
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Slf4j
abstract class AbstractPollingNotificationAgent implements Runnable {

  @Autowired
  ObjectMapper objectMapper

  abstract long getPollingInterval()
  abstract String getNotificationType()
  abstract void handleNotification(List<Map> input)

  final List<NotificationHandler> allNotificationHandler

  final List<NotificationHandler> agentNotificationHandlers = []

  AbstractPollingNotificationAgent(List<NotificationHandler> notificationHandlers) {
    this.allNotificationHandler = notificationHandlers
  }

  @PostConstruct
  void init() {
    for (handler in allNotificationHandler) {
      if (handler.handles(getNotificationType())) {
        agentNotificationHandlers << handler
      }
    }
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this, 0, pollingInterval, TimeUnit.SECONDS)
  }

  void notify(Map input) {
    for (handler in agentNotificationHandlers) {
      handler.handle input
    }
  }
}
