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
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

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
            snsClient(),
            sqsClient(),
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
            snsClient(),
            sqsClient(),
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
    SqsClient sqsClient = mock(SqsClient.class);
    Supplier<Boolean> disabled = spy(Supplier.class);
    doReturn(false).when(disabled).get();
    subscriber =
        new SQSSubscriber(
            subscription(),
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            snsClient(),
            sqsClient,
            disabled,
            new DefaultRegistry());

    // when
    subscriber.listenForMessages();

    // then
    verify(sqsClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
  }

  @Test
  @DisplayName("initializeQueue resolves URL only when skipQueueBootstrap=true")
  void testInitializeQueueSkipsBootstrapWhenFlagTrue() {
    // given
    AmazonPubsubProperties.AmazonPubsubSubscription sub = subscription();
    sub.setSkipQueueBootstrap(true);

    SqsClient sqs = sqsClient();
    SnsClient sns = snsClient();
    subscriber =
        new SQSSubscriber(
            sub,
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            sns,
            sqs,
            enableOnce(),
            new DefaultRegistry());

    // when
    subscriber.initializeQueue();

    // then — only getQueueUrl called, no setQueueAttributes or subscribe
    verify(sqs, times(1)).getQueueUrl(any(Consumer.class));
    verify(sqs, never()).setQueueAttributes(any(SetQueueAttributesRequest.class));
    verify(sns, never()).subscribe(any(SubscribeRequest.class));
  }

  @Test
  @DisplayName("initializeQueue bootstraps queue when skipQueueBootstrap=false (default)")
  void testInitializeQueueBootstrapsWhenFlagFalse() {
    // given — default subscription has skipQueueBootstrap=false
    SqsClient sqs = sqsClient();
    SnsClient sns = snsClient();
    subscriber =
        new SQSSubscriber(
            subscription(),
            mock(AmazonPubsubMessageHandler.class),
            mock(AmazonMessageAcknowledger.class),
            sns,
            sqs,
            enableOnce(),
            new DefaultRegistry());

    // when
    subscriber.initializeQueue();

    // then
    verify(sqs, times(1)).getQueueUrl(any(Consumer.class));
    verify(sqs, times(1)).setQueueAttributes(any(SetQueueAttributesRequest.class));
    verify(sns, times(1)).subscribe(any(SubscribeRequest.class));
  }

  AmazonPubsubProperties.AmazonPubsubSubscription subscription() {
    AmazonPubsubProperties.AmazonPubsubSubscription subscription =
        new AmazonPubsubProperties.AmazonPubsubSubscription();
    subscription.setName("name");
    subscription.setTopicARN("arn:aws:sns:us-east-2:123456789012:MyTopic");
    subscription.setQueueARN("arn:aws:sqs:us-east-2:123456789012:MyQueue");
    return subscription;
  }

  SqsClient sqsClient() {
    Message msg = Message.builder().messageId("msg-1").receiptHandle("handle-1").body("{}").build();

    GetQueueUrlResponse getQueueUrlResponse =
        GetQueueUrlResponse.builder().queueUrl("https://queueUrl").build();

    ReceiveMessageResponse receiveMessageResponse =
        ReceiveMessageResponse.builder().messages(List.of(msg)).build();

    SqsClient sqsClient = mock(SqsClient.class);
    doReturn(getQueueUrlResponse).when(sqsClient).getQueueUrl(any(Consumer.class));
    doReturn(receiveMessageResponse)
        .when(sqsClient)
        .receiveMessage(any(ReceiveMessageRequest.class));

    return sqsClient;
  }

  SnsClient snsClient() {
    SubscribeResponse subscribeResponse =
        SubscribeResponse.builder()
            .subscriptionArn("arn:aws:sqs:us-east-2:123456789012:MySubscription")
            .build();

    SnsClient snsClient = mock(SnsClient.class);
    doReturn(subscribeResponse).when(snsClient).subscribe(any(SubscribeRequest.class));

    return snsClient;
  }

  Supplier<Boolean> enableOnce() {
    Supplier<Boolean> sup = spy(Supplier.class);
    doReturn(true, false).when(sup).get();
    return sup;
  }
}
