/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pubsub.aws;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Responds to the SQS queue for each message using the unique messageReceiptHandle of the message.
 */
public class AmazonMessageAcknowledger implements MessageAcknowledger {

  private static final Logger log = LoggerFactory.getLogger(AmazonMessageAcknowledger.class);

  private SqsClient sqsClient;
  private String queueUrl;
  private Message message;
  private Registry registry;
  private String subscriptionName;

  public AmazonMessageAcknowledger(
      SqsClient sqsClient,
      String queueUrl,
      Message message,
      Registry registry,
      String subscriptionName) {
    this.sqsClient = sqsClient;
    this.queueUrl = queueUrl;
    this.message = message;
    this.registry = registry;
    this.subscriptionName = subscriptionName;
  }

  @Override
  public void ack() {
    try {
      sqsClient.deleteMessage(
          DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build());
      registry.counter(getProcessedMetricId(subscriptionName)).increment();
    } catch (SqsException e) {
      log.warn("Failed to delete message from queue {}: {}", queueUrl, e.getMessage());
    }
  }

  @Override
  public void nack() {
    // Do nothing. Message is being processed by another worker,
    // and will be available again in 30 seconds to process
    registry.counter(getNackMetricId(subscriptionName)).increment();
  }

  Id getProcessedMetricId(String subscriptionName) {
    return registry.createId(
        "echo.pubsub.amazon.totalProcessed", "subscriptionName", subscriptionName);
  }

  Id getNackMetricId(String subscriptionName) {
    return registry.createId(
        "echo.pubsub.amazon.messagesNacked", "subscriptionName", subscriptionName);
  }
}
