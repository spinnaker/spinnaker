/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.pubsub.aws;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SQSSubscriberTest {

  SQSSubscriber subscriber;
  AmazonMessageAcknowledger messageAcknowledger;

  @Test
  @DisplayName("acknowledger acknowledges when handler succeeds")
  void testAcknowledgerAcksHandlerSucceeds() {
    // given
    messageAcknowledger = mock(AmazonMessageAcknowledger.class);
    subscriber =
        new SQSSubscriber(
            subscription(),
            mock(AmazonPubsubMessageHandler.class),
            messageAcknowledger,
            amazonSNS(),
            amazonSQS(),
            enableOnce(),
            new DefaultRegistry());

    // when
    subscriber.initializeQueue();
    subscriber.listenForMessages();

    // then
    verify(messageAcknowledger, times(1)).ack(any(), any());
    verify(messageAcknowledger, never()).nack(any(), any());
  }

  @Test
  @DisplayName("acknowledger nacks when handler fails")
  void testAcknowledgerNackHandlerFails() {
    // given
    messageAcknowledger = mock(AmazonMessageAcknowledger.class);
    AmazonPubsubMessageHandler throwyHandler = spy(AmazonPubsubMessageHandler.class);
    doThrow(new RuntimeException("unhappy handler")).when(throwyHandler).handleMessage(any());
    subscriber =
        new SQSSubscriber(
            subscription(),
            throwyHandler,
            messageAcknowledger,
            amazonSNS(),
            amazonSQS(),
            enableOnce(),
            new DefaultRegistry());

    // when
    subscriber.initializeQueue();
    subscriber.listenForMessages();

    // then
    verify(messageAcknowledger, never()).ack(any(), any());
    verify(messageAcknowledger, times(1)).nack(any(), any());
  }

  @Test
  @DisplayName("the subscriber does not query SQS when disabled")
  void testSubscriberNotQuerySQSDisabled() {
    // given
    AmazonSQS amazonSQS = mock(AmazonSQS.class);
    Supplier disabled = spy(Supplier.class);
    doReturn(false).when(disabled).get();
    subscriber =
        new SQSSubscriber(
            subscription(),
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            amazonSNS(),
            amazonSQS,
            disabled,
            new DefaultRegistry());

    // when
    subscriber.listenForMessages();

    // then
    verify(amazonSQS, never()).receiveMessage(any(ReceiveMessageRequest.class));
  }

  AmazonPubsubProperties.AmazonPubsubSubscription subscription() {
    AmazonPubsubProperties.AmazonPubsubSubscription subscription =
        new AmazonPubsubProperties.AmazonPubsubSubscription();
    subscription.setName("name");
    subscription.setTopicARN("arn:aws:sns:us-east-2:123456789012:MyTopic");
    subscription.setQueueARN("arn:aws:sqs:us-east-2:123456789012:MyQueue");
    return subscription;
  }

  AmazonSQS amazonSQS() {
    Message msg = spy(new Message());

    GetQueueUrlResult getQueueUrlResult = spy(new GetQueueUrlResult());
    doReturn("https://queueUrl").when(getQueueUrlResult).getQueueUrl();

    ReceiveMessageResult receiveMessageResult = spy(ReceiveMessageResult.class);
    doReturn(List.of(msg)).when(receiveMessageResult).getMessages();

    AmazonSQS SQS = spy(AmazonSQS.class);
    doReturn(getQueueUrlResult).when(SQS).getQueueUrl(anyString());
    doReturn(receiveMessageResult).when(SQS).receiveMessage(any(ReceiveMessageRequest.class));

    return SQS;
  }

  AmazonSNS amazonSNS() {
    SubscribeResult subscribeResult = spy(new SubscribeResult());
    doReturn("arn:aws:sqs:us-east-2:123456789012:MySubscription")
        .when(subscribeResult)
        .getSubscriptionArn();

    AmazonSNS SNS = spy(AmazonSNS.class);
    doReturn(subscribeResult).when(SNS).subscribe(anyString(), anyString(), anyString());

    return SNS;
  }

  Supplier enableOnce() {
    Supplier sup = spy(Supplier.class);
    doReturn(true, false).when(sup).get();

    return sup;
  }
}
