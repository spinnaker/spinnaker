/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.interlink.aws;

import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.pubsub.aws.NotificationMessage;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler;
import com.netflix.spinnaker.orca.interlink.InterlinkMessageHandlingException;
import com.netflix.spinnaker.orca.interlink.events.InterlinkEvent;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InterlinkAmazonMessageHandler implements AmazonPubsubMessageHandler {
  private final ObjectMapper objectMapper;
  private final ExecutionRepository executionRepository;

  public InterlinkAmazonMessageHandler(
      ObjectMapper objectMapper, ExecutionRepository executionRepository) {
    this.objectMapper = objectMapper;
    this.executionRepository = executionRepository;
  }

  @Override
  public void handleMessage(Message message) {
    try {
      NotificationMessage snsMessage =
          objectMapper.readValue(message.getBody(), NotificationMessage.class);
      InterlinkEvent event = objectMapper.readValue(snsMessage.getMessage(), InterlinkEvent.class);
      log.debug("Received interlink event {}", event);

      handleInternal(event);
    } catch (JsonProcessingException e) {
      throw new InterlinkMessageHandlingException(e);
    }
  }

  @VisibleForTesting
  void handleInternal(InterlinkEvent event) {
    if (executionRepository.handlesPartition(event.getPartition())) {
      event.applyTo(executionRepository);
    } else {
      log.debug(
          "Execution repository with local partition {} does not handle this event's partition {}, "
              + "so it will not be applied",
          executionRepository.getPartition(),
          event.getPartition());
    }
  }
}
