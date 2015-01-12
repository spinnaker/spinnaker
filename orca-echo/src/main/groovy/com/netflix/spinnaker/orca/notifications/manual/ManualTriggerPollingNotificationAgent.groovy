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

package com.netflix.spinnaker.orca.notifications.manual

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ManualTriggerPollingNotificationAgent extends AbstractPollingNotificationAgent {

  public static final String NOTIFICATION_TYPE = "manualPipelineTrigger"
  long pollingInterval = 10

  @Autowired
  ManualTriggerPollingNotificationAgent(ObjectMapper objectMapper,
                                        EchoService echoService,
                                        Client jesqueClient,
                                        List<NotificationHandler> handlers) {
    super(objectMapper, echoService, jesqueClient, handlers)
  }

  @Override
  String getNotificationType() {
    NOTIFICATION_TYPE
  }

  @Override
  void handleNotification(List<Map> response) {
    for (event in response) {
      notify event.content
    }
  }
}
