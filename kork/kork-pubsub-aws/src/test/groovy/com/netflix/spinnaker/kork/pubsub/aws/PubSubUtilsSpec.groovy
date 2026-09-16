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

package com.netflix.spinnaker.kork.pubsub.aws

import com.netflix.spinnaker.kork.aws.ARN
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest
import spock.lang.Specification

import java.util.function.Consumer

class PubSubUtilsSpec extends Specification {
  SqsClient sqsClient = Mock()

  ARN queueARN = new ARN("arn:aws:sqs:us-west-2:100:queueName")
  ARN topicARN = new ARN("arn:aws:sns:us-west-2:100:topicName")

  def "getQueueUrl returns URL and passes QueueOwnerAWSAccountId"() {
    when:
    def url = PubSubUtils.getQueueUrl(sqsClient, queueARN)

    then:
    url == "my-queue-url"
    1 * sqsClient.getQueueUrl(_ as Consumer) >> { Consumer<GetQueueUrlRequest.Builder> consumer ->
      // Verify the consumer sets the right values by building the request
      def builder = GetQueueUrlRequest.builder()
      consumer.accept(builder)
      def req = builder.build()
      assert req.queueName() == queueARN.name
      assert req.queueOwnerAWSAccountId() == queueARN.account
      return GetQueueUrlResponse.builder().queueUrl("my-queue-url").build()
    }
    0 * _
  }

  def "getQueueUrl propagates QueueDoesNotExistException (no createQueue fallback)"() {
    when:
    PubSubUtils.getQueueUrl(sqsClient, queueARN)

    then:
    // retrySupport retries MAX_RETRIES (=5) times before giving up
    (1.._) * sqsClient.getQueueUrl(_ as Consumer) >> {
      throw QueueDoesNotExistException.builder().message("nope").build()
    }
    0 * sqsClient.createQueue(_ as CreateQueueRequest)
    thrown(QueueDoesNotExistException)
  }

  def "ensureQueueExists does not create queue if it exists"() {
    when:
    def queueId = PubSubUtils.ensureQueueExists(sqsClient, queueARN, topicARN, 1)

    then:
    queueId == "my-queue-url"
    1 * sqsClient.getQueueUrl(_ as Consumer) >> {
      GetQueueUrlResponse.builder().queueUrl("my-queue-url").build()
    }
    0 * sqsClient.createQueue(_ as CreateQueueRequest)
    1 * sqsClient.setQueueAttributes(_ as SetQueueAttributesRequest) >> { SetQueueAttributesRequest req ->
      assert req.queueUrl() == "my-queue-url"
      assert req.attributes().get(QueueAttributeName.POLICY) == PubSubUtils.buildSQSPolicy(queueARN, topicARN).toJson()
      assert req.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD) == "1"
      return null
    }
    0 * _
  }

  def "ensureQueueExists falls back to createQueue when queue is missing"() {
    when:
    def queueId = PubSubUtils.ensureQueueExists(sqsClient, queueARN, topicARN, 1)

    then:
    queueId == "my-queue-url"
    // retry may re-invoke getQueueUrl before giving up and returning to ensureQueueExists
    (1.._) * sqsClient.getQueueUrl(_ as Consumer) >> {
      throw QueueDoesNotExistException.builder().message("nope").build()
    }
    1 * sqsClient.createQueue(_ as CreateQueueRequest) >> { CreateQueueRequest req ->
      assert req.queueName() == queueARN.name
      return CreateQueueResponse.builder().queueUrl("my-queue-url").build()
    }
    1 * sqsClient.setQueueAttributes(_ as SetQueueAttributesRequest) >> { SetQueueAttributesRequest req ->
      assert req.queueUrl() == "my-queue-url"
      assert req.attributes().get(QueueAttributeName.POLICY) == PubSubUtils.buildSQSPolicy(queueARN, topicARN).toJson()
      assert req.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD) == "1"
      return null
    }
    0 * _
  }
}
