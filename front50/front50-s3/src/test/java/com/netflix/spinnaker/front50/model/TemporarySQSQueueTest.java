/*
 * Copyright 2025 OpsMx, Inc.
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
package com.netflix.spinnaker.front50.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sns.model.UnsubscribeResponse;
import software.amazon.awssdk.services.sns.paginators.ListTopicsIterable;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse;

@ExtendWith(MockitoExtension.class)
class TemporarySQSQueueTest {

  @Mock SqsClient sqsClient;

  @Mock SnsClient snsClient;

  @Test
  void shouldCreateAndTeardownTemporarySqsQueue() {
    Topic topic1 = Topic.builder().topicArn("arn:aws:sns:us-west-2:123:not_my_topic").build();
    Topic topic2 = Topic.builder().topicArn("arn:aws:sns:us-west-2:123:my_topic").build();

    ListTopicsIterable paginator = mock(ListTopicsIterable.class);
    SdkIterable<Topic> topics = mock(SdkIterable.class);
    when(topics.stream()).thenReturn(Stream.of(topic1, topic2));
    when(paginator.topics()).thenReturn(topics);
    when(snsClient.listTopicsPaginator(any(ListTopicsRequest.class))).thenReturn(paginator);

    CreateQueueResponse createQueueResponse =
        CreateQueueResponse.builder().queueUrl("http://my/queue_url").build();
    when(sqsClient.createQueue(any(CreateQueueRequest.class))).thenReturn(createQueueResponse);

    SubscribeResponse subscribeResponse =
        SubscribeResponse.builder().subscriptionArn("arn:subscription").build();
    when(snsClient.subscribe(any(SubscribeRequest.class))).thenReturn(subscribeResponse);

    SetQueueAttributesResponse setQueueAttributesResponse =
        SetQueueAttributesResponse.builder().build();
    when(sqsClient.setQueueAttributes(any(SetQueueAttributesRequest.class)))
        .thenReturn(setQueueAttributesResponse);

    TemporarySQSQueue queue = new TemporarySQSQueue(sqsClient, snsClient, "my_topic", "my_id");

    TemporarySQSQueue.TemporaryQueue tempQueue = queue.getTemporaryQueue();
    assertEquals("arn:aws:sns:us-west-2:123:my_topic", tempQueue.snsTopicArn);
    assertEquals("arn:aws:sqs:us-west-2:123:my_topic__my_id", tempQueue.sqsQueueArn);
    assertEquals("http://my/queue_url", tempQueue.sqsQueueUrl);
    assertEquals("arn:subscription", tempQueue.snsTopicSubscriptionArn);

    DeleteQueueResponse deleteQueueResponse = DeleteQueueResponse.builder().build();
    when(sqsClient.deleteQueue(any(DeleteQueueRequest.class))).thenReturn(deleteQueueResponse);

    UnsubscribeResponse unsubscribeResponse = UnsubscribeResponse.builder().build();
    when(snsClient.unsubscribe(any(UnsubscribeRequest.class))).thenReturn(unsubscribeResponse);

    queue.shutdown();

    verify(sqsClient).deleteQueue(any(DeleteQueueRequest.class));
    verify(snsClient).unsubscribe(any(UnsubscribeRequest.class));
  }

  @Test
  void shouldConvertInstanceIdToSqsQueueArnFriendly() {
    assertEquals("127_0_0_1", TemporarySQSQueue.getSanitizedInstanceId("127.0.0.1"));
    assertEquals("my-host_local", TemporarySQSQueue.getSanitizedInstanceId("my-host.local"));
    assertEquals("localhoser", TemporarySQSQueue.getSanitizedInstanceId("localhoser"));
    assertEquals(
        "something123-ABC_def", TemporarySQSQueue.getSanitizedInstanceId("something123-ABC.def"));
  }
}
