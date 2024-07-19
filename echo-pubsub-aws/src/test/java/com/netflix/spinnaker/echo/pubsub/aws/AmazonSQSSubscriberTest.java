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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.config.AmazonPubsubProperties;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AmazonSQSSubscriberTest {

  @Mock private AmazonSNS amazonSNS;

  @Mock private AmazonSQS amazonSQS;

  @Mock private PubsubMessageHandler pubsubMessageHandler;

  @Mock private Registry registry;

  private final ARN queueARN = new ARN("arn:aws:sqs:us-west-2:100:queueName");
  private final ARN topicARN = new ARN("arn:aws:sns:us-west-2:100:topicName");

  private AmazonPubsubProperties.AmazonPubsubSubscription subscription =
      new AmazonPubsubProperties.AmazonPubsubSubscription(
          "aws_events", topicARN.getArn(), queueARN.getArn(), "", null, null, 3600);

  private ObjectMapper objectMapper = EchoObjectMapper.getInstance();

  private SQSSubscriber subject;

  @BeforeEach
  void setUp() {
    subject =
        new SQSSubscriber(
            objectMapper,
            subscription,
            pubsubMessageHandler,
            amazonSNS,
            amazonSQS,
            () -> true,
            registry);
  }

  @Test
  void shouldUnmarshalSNSNotificationMessage() throws JsonProcessingException {
    // Given
    MemoryAppender memoryAppender = new MemoryAppender(SQSSubscriber.class);

    String payload =
        "{\n"
            + "  \"Records\": [\n"
            + "    {\n"
            + "      \"eventVersion\": \"2.0\",\n"
            + "      \"eventSource\": \"aws:s3\",\n"
            + "      \"awsRegion\": \"us-west-2\",\n"
            + "      \"eventName\": \"ObjectCreated:Put\",\n"
            + "      \"s3\": {\n"
            + "        \"s3SchemaVersion\": \"1.0\",\n"
            + "        \"configurationId\": \"prestaging_front50_events\",\n"
            + "        \"bucket\": {\n"
            + "          \"name\": \"us-west-2.spinnaker-prod\",\n"
            + "          \"ownerIdentity\": {\n"
            + "            \"principalId\": \"A2TW6LBRCW9VEM\"\n"
            + "          },\n"
            + "          \"arn\": \"arn:aws:s3:::us-west-2.spinnaker-prod\"\n"
            + "        },\n"
            + "        \"object\": {\n"
            + "          \"key\": \"prestaging/front50/pipelines/31ef9c67-1d67-474f-a653-ac4b94c90817/pipeline-metadata.json\",\n"
            + "          \"versionId\": \"8eyu4_RfV8EUqTnClhkKfLK5V4El_mIW\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    NotificationMessageWrapper notificationMessage =
        new NotificationMessageWrapper(
            "Notification",
            "4444-ffff",
            "arn:aws:sns:us-west-2:100:topicName",
            "Amazon S3 Notification",
            payload,
            Collections.emptyMap());

    String snsMessage = objectMapper.writeValueAsString(notificationMessage);

    // When
    String result = subject.unmarshalMessageBody(snsMessage);

    // Then
    List<String> logMsgs =
        memoryAppender.search(
            "Unable unmarshal NotificationMessageWrapper. Unknown message type", Level.ERROR);
    assertThat(logMsgs).hasSize(0);
    assertEquals(payload, result);
  }
}
