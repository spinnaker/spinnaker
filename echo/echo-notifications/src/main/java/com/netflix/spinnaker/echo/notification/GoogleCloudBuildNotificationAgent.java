/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Handles Google Cloud Build notifications by forwarding information on the completed build to
 * igor.
 */
@ConditionalOnProperty("gcb.enabled")
@RequiredArgsConstructor
@Service
@Slf4j
public class GoogleCloudBuildNotificationAgent implements EventListener {
  private final IgorService igorService;
  private final RetrySupport retrySupport;

  @Override
  public void processEvent(Event event) {
    if (event.getDetails() != null && event.getDetails().getType().equals("googleCloudBuild")) {
      MessageDescription messageDescription =
          (MessageDescription) event.getContent().get("messageDescription");
      retrySupport.retry(
          () ->
              AuthenticatedRequest.allowAnonymous(
                  () ->
                      igorService.updateBuildStatus(
                          messageDescription.getSubscriptionName(),
                          messageDescription.getMessageAttributes().get("buildId"),
                          messageDescription.getMessageAttributes().get("status"),
                          RequestBody.create(
                              messageDescription.getMessagePayload(),
                              MediaType.parse("application/json")))),
          5,
          2000,
          false);
    }
  }
}
