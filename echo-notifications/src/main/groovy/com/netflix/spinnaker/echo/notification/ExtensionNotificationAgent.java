/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.NotificationAgent;
import java.util.Map;

public class ExtensionNotificationAgent extends AbstractEventNotificationAgent {

  private final NotificationAgent agent;

  public ExtensionNotificationAgent(NotificationAgent agent) {
    this.agent = agent;
  }

  @Override
  public String getNotificationType() {
    return agent.getNotificationType();
  }

  @Override
  public void sendNotifications(
      Map notification, String application, Event event, Map config, String status) {
    agent.sendNotifications(notification, application, event, status);
  }
}
