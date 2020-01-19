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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.Notification.InteractiveActionCallback
import com.netflix.spinnaker.echo.controller.EchoResponse
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity

interface NotificationService {
  boolean supportsType(Notification.Type type)
  EchoResponse handle(Notification notification)
}

/**
 * Extension of the {@link NotificationService} interface that defines methods for services supporting interactive
 * notifications by means of callbacks from the corresponding external service into echo
 * (via {@code POST /notifications/callbacks}).
 *
 * Processing of those callbacks happens in three steps:
 *
 * 1. The {@link #parseInteractionCallback} method is called to translate the service-specific payload received in the
 *    callback request to the generic equivalent defined by echo, defined by the {@link InteractiveActionCallback}
 *    class.
 *
 * 2. Using de identifier of the Spinnaker service which initially originated the notification, and which is parsed
 *    in step #1 and returned in {@link InteractiveActionCallback#serviceId}, echo relays the callback details, now
 *    in the generic format, to the corresponding Spinnaker service for processing. This allows the originating service
 *    to take action based on the user's response to the notification (e.g. if the notification was to request approval
 *    and the user clicked an "Approve" button, that would be represented in the object passed to the service).
 *
 * 3. The {@link #respondToCallback} method is called to allow the implementor of the interface to respond back to
 *    the external notification service as needed (e.g. Slack includes a {@code response_url} field in the payload
 *    which allows us to interact again with the original notification message by responding to a thread, replacing
 *    the contents of the original notification with the user's choice, etc.).
 */
interface InteractiveNotificationService extends NotificationService {
  /**
   * Translate the contents received by echo on the generic notification callbacks API into a generic callback
   * object that can be forwarded to downstream Spinnaker services for actual processing.
   * @param content
   * @return
   */
  InteractiveActionCallback parseInteractionCallback(RequestEntity<String> request)

  /**
   * Gives an opportunity to the notification service to respond to the original callback in a service-specific
   * manner (e.g. Slack provides a `response_url` in the payload that can be called to interact with the original
   * Slack notification message).
   *
   * @param content
   */
  Optional<ResponseEntity<String>> respondToCallback(RequestEntity<String> request)
}
