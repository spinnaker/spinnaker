/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.googlechat;

import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.controller.EchoResponse;
import com.netflix.spinnaker.echo.notification.NotificationService;
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("googlechat.enabled")
class GoogleChatNotificationService implements NotificationService {
  private static Notification.Type TYPE = Notification.Type.GOOGLECHAT;

  @Autowired GoogleChatService chat;

  @Autowired NotificationTemplateEngine notificationTemplateEngine;

  @Override
  public boolean supportsType(Notification.Type type) {
    return type == TYPE;
  }

  @Override
  public EchoResponse.Void handle(Notification notification) {
    String body =
        notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY);

    Collection<String> addressSet = notification.getTo();
    for (String addr : addressSet) {
      // In Chat, users can only copy the whole link easily. We just extract the information from
      // the whole link.
      // Example: https://chat.googleapis.com/v1/spaces/{partialWebhookUrl}
      String baseUrl = "https://chat.googleapis.com/v1/spaces/";
      String completeLink = addr;
      String partialWebhookURL = completeLink.substring(baseUrl.length());
      chat.sendMessage(partialWebhookURL, new GoogleChatMessage(body));
    }
    return new EchoResponse.Void();
  }
}
