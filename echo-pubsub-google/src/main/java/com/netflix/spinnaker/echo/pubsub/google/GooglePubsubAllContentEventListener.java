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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.config.GooglePubsubProperties.Content;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubPublishers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnExpression("${pubsub.enabled:false} && ${pubsub.google.enabled:false}")
public class GooglePubsubAllContentEventListener implements EventListener {

  @Autowired private PubsubPublishers publishers;

  @Autowired ObjectMapper mapper;

  @Override
  public void processEvent(Event event) {
    publishers.publishersMatchingType(PubsubSystem.GOOGLE).stream()
        .map(p -> (GooglePubsubPublisher) p)
        .filter(p -> p.getContent() == Content.ALL)
        .forEach(p -> p.publishEvent(event));
  }
}
