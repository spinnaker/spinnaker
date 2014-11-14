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
import com.netflix.spinnaker.orca.echo.EchoService
import redis.clients.jedis.JedisCommands

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BuildJobPollingNotificationAgent implements Runnable {

  static final String NOTIFICATION_TYPE = "build"

  @Autowired
  EchoService echoService

  @Autowired
  ObjectMapper objectMapper

  private long lastCheck = System.currentTimeMillis()
  final List<NotificationHandler> buildNotificationHandlers = []

  @Autowired
  BuildJobPollingNotificationAgent(List<NotificationHandler> notificationHandlers) {
    for (handler in notificationHandlers) {
      if (handler.handles(NOTIFICATION_TYPE)) {
        buildNotificationHandlers << handler
      }
    }
  }

  @PostConstruct
  void init() {
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this, 0, 120, TimeUnit.SECONDS)
  }

  @Override
  void run() {
    try {
      def response = echoService.getEvents(lastCheck, 10000, true, NOTIFICATION_TYPE)
      lastCheck = System.currentTimeMillis()
      def resp = objectMapper.readValue(response.body.in().text, Map)
      for (event in resp.hits) {
        if (event.containsKey("project") && event.containsKey("master")) {
          for (handler in buildNotificationHandlers) {
            Map input = event.project as Map
            input.master = event.master
            handler.handle(input)
          }
        }
      }
    } catch (e) {
      e.printStackTrace()
    }
  }
}
