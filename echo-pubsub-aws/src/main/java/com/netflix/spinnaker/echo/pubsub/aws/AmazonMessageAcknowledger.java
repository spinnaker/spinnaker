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

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responds to the SQS queue for each message using the unique messageReceiptHandle
 * of the message.
 */
public class AmazonMessageAcknowledger implements MessageAcknowledger {

  private static final Logger log = LoggerFactory.getLogger(AmazonMessageAcknowledger.class);

  private AmazonSQS amazonSQS;
  private String queueUrl;
  private Message message;
  private Registry registry;
  private String subscriptionName;

  public AmazonMessageAcknowledger(AmazonSQS amazonSQS, String queueUrl, Message message, Registry registry, String subscriptionName) {
    this.amazonSQS = amazonSQS;
    this.queueUrl = queueUrl;
    this.message = message;
    this.registry = registry;
    this.subscriptionName = subscriptionName;
  }

  @Override
  public void ack() {
    // Delete from queue
    try {
      amazonSQS.deleteMessage(queueUrl, message.getReceiptHandle());
      log.debug("Deleted message: {} from queue {}", message.getMessageId(), queueUrl);
      registry.counter(getProcessedMetricId(subscriptionName)).increment();
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn(
        "Error deleting message: {}, queue: {}, reason: {} (receiptHandle: {})",
        message.getMessageId(),
        queueUrl,
        e.getMessage(),
        message.getReceiptHandle()
      );
    }
  }

  @Override
  public void nack() {
    // Set visibility timeout to 0, so that the message can be processed by another worker
    // Todo emjburns: is changing message visibility a needed optimization?
    try {
      amazonSQS.changeMessageVisibility(queueUrl, message.getReceiptHandle(), 0);
      log.debug("Changed visibility timeout of message: {} from queue: {}", message.getMessageId(), queueUrl);
      registry.counter(getFailedMetricId(subscriptionName)).increment();
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn("Error nack-ing message: {}, queue: {}, reason: {} (receiptHandle: {})",
        message.getMessageId(),
        queueUrl,
        e.getMessage(),
        message.getReceiptHandle()
      );
    }
  }

  Id getProcessedMetricId(String subscriptionName) {
    return registry.createId("echo.pubsub.amazon.totalProcessed", "subscriptionName", subscriptionName);
  }

  Id getFailedMetricId(String subscriptionName) {
    return registry.createId("echo.pubsub.amazon.totalFailed", "subscriptionName", subscriptionName);
  }
}
