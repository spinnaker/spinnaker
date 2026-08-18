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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class SQSSubscriberTest {

  SQSSubscriber subscriber;
  AmazonMessageAcknowledger messageAcknowledger;

  @Test
  @DisplayName("acknowledger acknowledges when handler succeeds")
  void testAcknowledgerAcksHandlerSucceeds() {
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

    subscriber.initializeQueue();
    subscriber.listenForMessages();

    verify(messageAcknowledger, times(1)).ack(any(), any());
    verify(messageAcknowledger, never()).nack(any(), any());
  }

  @Test
  @DisplayName("acknowledger nacks when handler fails")
  void testAcknowledgerNackHandlerFails() {
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

    subscriber.initializeQueue();
    subscriber.listenForMessages();

    verify(messageAcknowledger, never()).ack(any(), any());
    verify(messageAcknowledger, times(1)).nack(any(), any());
  }

  @Test
  @DisplayName("the subscriber does not query SQS when disabled")
  void testSubscriberNotQuerySQSDisabled() {
    SqsClient sqsClient = mock(SqsClient.class);
    Supplier disabled = spy(Supplier.class);
    doReturn(false).when(disabled).get();
    subscriber =
        new SQSSubscriber(
            subscription(),
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            amazonSNS(),
            sqsClient,
            disabled,
            new DefaultRegistry());

    subscriber.listenForMessages();

    verify(sqsClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
  }

  @Test
  @DisplayName("initializeQueue resolves URL only when skipQueueBootstrap=true")
  void testInitializeQueueSkipsBootstrapWhenFlagTrue() {
    AmazonPubsubProperties.AmazonPubsubSubscription sub = subscription();
    sub.setSkipQueueBootstrap(true);

    SqsClient sqs = amazonSQS();
    SnsClient sns = amazonSNS();
    subscriber =
        new SQSSubscriber(
            sub,
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            sns,
            sqs,
            enableOnce(),
            new DefaultRegistry());

    subscriber.initializeQueue();

    verify(sqs, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
    verify(sqs, never())
        .createQueue(any(software.amazon.awssdk.services.sqs.model.CreateQueueRequest.class));
    verify(sqs, never())
        .setQueueAttributes(
            any(software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest.class));
    verify(sns, never())
        .subscribe(any(software.amazon.awssdk.services.sns.model.SubscribeRequest.class));
  }

  @Test
  @DisplayName("initializeQueue bootstraps queue when skipQueueBootstrap=false (default)")
  void testInitializeQueueBootstrapsWhenFlagFalse() {
    SqsClient sqs = amazonSQS();
    SnsClient sns = amazonSNS();
    subscriber =
        new SQSSubscriber(
            subscription(),
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            sns,
            sqs,
            enableOnce(),
            new DefaultRegistry());

    subscriber.initializeQueue();

    verify(sqs, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
    verify(sqs, times(1))
        .setQueueAttributes(
            any(software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest.class));
    verify(sns, times(1))
        .subscribe(any(software.amazon.awssdk.services.sns.model.SubscribeRequest.class));
  }

  AmazonPubsubProperties.AmazonPubsubSubscription subscription() {
    AmazonPubsubProperties.AmazonPubsubSubscription subscription =
        new AmazonPubsubProperties.AmazonPubsubSubscription();
    subscription.setName("name");
    subscription.setTopicARN("arn:aws:sns:us-east-2:123456789012:MyTopic");
    subscription.setQueueARN("arn:aws:sqs:us-east-2:123456789012:MyQueue");
    return subscription;
  }

  SqsClient amazonSQS() {
    Message msg = Message.builder().messageId("msg-1").receiptHandle("handle-1").body("{}").build();

    GetQueueUrlResponse getQueueUrlResponse =
        GetQueueUrlResponse.builder().queueUrl("https://queueUrl").build();

    ReceiveMessageResponse receiveMessageResponse =
        ReceiveMessageResponse.builder().messages(List.of(msg)).build();

    SqsClient sqsClient = spy(SqsClient.class);
    doReturn(getQueueUrlResponse).when(sqsClient).getQueueUrl(any(GetQueueUrlRequest.class));
    doReturn(receiveMessageResponse)
        .when(sqsClient)
        .receiveMessage(any(ReceiveMessageRequest.class));
    doReturn(software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse.builder().build())
        .when(sqsClient)
        .setQueueAttributes(
            any(software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest.class));

    return sqsClient;
  }

  SnsClient amazonSNS() {
    software.amazon.awssdk.services.sns.model.SubscribeResponse subscribeResponse =
        software.amazon.awssdk.services.sns.model.SubscribeResponse.builder()
            .subscriptionArn("arn:aws:sqs:us-east-2:123456789012:MySubscription")
            .build();

    SnsClient snsClient = spy(SnsClient.class);
    doReturn(subscribeResponse)
        .when(snsClient)
        .subscribe(any(software.amazon.awssdk.services.sns.model.SubscribeRequest.class));

    return snsClient;
  }

  Supplier enableOnce() {
    Supplier sup = spy(Supplier.class);
    doReturn(true, false).when(sup).get();
    return sup;
  }
}
