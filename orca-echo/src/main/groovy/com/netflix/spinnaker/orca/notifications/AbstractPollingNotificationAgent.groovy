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

import groovy.transform.CompileStatic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import net.greghaines.jesque.Job
import net.greghaines.jesque.client.Client

@CompileStatic
abstract class AbstractPollingNotificationAgent implements Runnable {

  private final ObjectMapper objectMapper
  private final Client jesqueClient
  private final EchoEventPoller echoEventPoller

  abstract long getPollingInterval()

  abstract String getNotificationType()

  abstract void handleNotification(List<Map> input)

  AbstractPollingNotificationAgent(ObjectMapper objectMapper,
                                   EchoEventPoller echoEventPoller,
                                   Client jesqueClient) {
    this.objectMapper = objectMapper
    this.echoEventPoller = echoEventPoller
    this.jesqueClient = jesqueClient
  }

  @PostConstruct
  void init() {
    Executors.newSingleThreadScheduledExecutor()
             .scheduleAtFixedRate(this, 0, pollingInterval, TimeUnit.SECONDS)
  }

  @Override
  void run() {
    try {
      def response = echoEventPoller.getEvents(notificationType)
      List<Map> maps = objectMapper.readValue(response.body.in().text, List)
      handleNotification maps
    } catch (e) {
      e.printStackTrace()
    }
  }

  // TODO: can we just use logical names rather than handler classes?
  abstract Class<? extends NotificationHandler> handlerType()

  void notify(Map<String, ?> input) {
    jesqueClient.enqueue(
        notificationType,
        new Job(handlerType().name, input)
    )
  }
}
