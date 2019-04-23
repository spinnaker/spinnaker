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

package com.netflix.spinnaker.echo.pubsub;

import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Metadata;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.pubsub.model.EventCreator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates Google Cloud Build events when a pubsub build notification is received.
 */
@Component
@ConditionalOnProperty("gcb.enabled")
@Slf4j
public class GoogleCloudBuildEventCreator implements EventCreator {
  private static final String EVENT_TYPE = "googleCloudBuild";

  public Event createEvent(MessageDescription description) {
    log.debug("Processing pubsub event with payload {}", description.getMessagePayload());

    Event event = new Event();
    Map<String, Object> content = new HashMap<>();
    content.put("messageDescription", description);

    Metadata details = new Metadata();
    details.setType(EVENT_TYPE);

    event.setContent(content);
    event.setDetails(details);
    return event;
  }
}
