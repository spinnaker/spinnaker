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

package com.netflix.spinnaker.echo.pubsub.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.api.Notification.Type;
import com.netflix.spinnaker.echo.config.GooglePubsubProperties.Content;
import com.netflix.spinnaker.echo.controller.EchoResponse;
import com.netflix.spinnaker.echo.controller.EchoResponse.Void;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.notification.AbstractEventNotificationAgent;
import com.netflix.spinnaker.echo.notification.NotificationService;
import com.netflix.spinnaker.echo.pubsub.PubsubPublishers;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnExpression("${pubsub.enabled:false} && ${pubsub.google.enabled:false}")
public class GooglePubsubNotificationEventListener extends AbstractEventNotificationAgent implements
  NotificationService {

  private static Type TYPE = Type.PUBSUB;

  @Autowired
  private PubsubPublishers publishers;

  /**
   * Handles calls caught and processed by the superclass from the HistoryController invocations.
   */
  @Override
  public void sendNotifications(Map notification, String application, Event event, Map config,
    String status) {
    publishers.publishersMatchingType(PubsubSystem.GOOGLE)
      .stream()
      .map(p -> (GooglePubsubPublisher) p)
      .filter(p -> p.getContent() == Content.NOTIFICATIONS)
      .filter(p -> notification.containsKey("topic") && notification.get("topic").toString()
        .equalsIgnoreCase(p.getTopicName()))
      .forEach(p -> p.publishEvent(event));
  }

  /**
   * Handles explicit calls from the NotificationsController - generally only used with Manual
   * Judgement stage from Orca.
   */
  @Override
  public EchoResponse.Void handle(Notification notification) {
    if (log.isDebugEnabled() && mapper != null) {
      try {
        log.debug("Notification received: " + mapper.writerWithDefaultPrettyPrinter()
          .writeValueAsString(notification));
      } catch (JsonProcessingException jpe) {
        log.warn("Error parsing notification", jpe);
      }
    }

    if (notification.getTo() == null || notification.getTo().isEmpty()) {
      return new Void();
    }
    String topic = notification.getTo().iterator().next();

    publishers.publishersMatchingType(PubsubSystem.GOOGLE)
      .stream()
      .map(p -> (GooglePubsubPublisher) p)
      .filter(p -> StringUtils.equalsIgnoreCase(topic, p.getTopicName()))
      .forEach(p -> publishNotification(p, notification));
    return new EchoResponse.Void();
  }

  @Override
  public boolean supportsType(Type type) {
    return type == TYPE;
  }

  @Override
  public String getNotificationType() {
    return TYPE.toString().toLowerCase();
  }

  private void publishNotification(GooglePubsubPublisher p, Notification notification) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("application", notification.getSource().getApplication());
    attributes.put("type", notification.getSource().getExecutionType());
    attributes.values().removeIf(Objects::isNull);

    p.publish(notification.getAdditionalContext(), attributes);
  }
}
