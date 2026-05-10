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

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueResult
import com.amazonaws.services.sqs.model.GetQueueUrlRequest
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.netflix.spinnaker.kork.aws.ARN
import spock.lang.Specification

class PubSubUtilsSpec extends Specification {
  AmazonSNS amazonSNS = Mock()
  AmazonSQS amazonSQS = Mock()

  ARN queueARN = new ARN("arn:aws:sqs:us-west-2:100:queueName")
  ARN topicARN = new ARN("arn:aws:sns:us-west-2:100:topicName")

  def "getQueueUrl returns URL and passes QueueOwnerAWSAccountId"() {
    when:
    def url = PubSubUtils.getQueueUrl(amazonSQS, queueARN)

    then:
    url == "my-queue-url"
    1 * amazonSQS.getQueueUrl({ GetQueueUrlRequest req ->
      req.queueName == queueARN.name && req.queueOwnerAWSAccountId == queueARN.account
    }) >> { new GetQueueUrlResult().withQueueUrl("my-queue-url") }
    0 * _
  }

  def "getQueueUrl propagates QueueDoesNotExistException (no createQueue fallback)"() {
    when:
    PubSubUtils.getQueueUrl(amazonSQS, queueARN)

    then:
    // retrySupport retries MAX_RETRIES (=5) times before giving up
    (1.._) * amazonSQS.getQueueUrl(_ as GetQueueUrlRequest) >> { throw new QueueDoesNotExistException("nope") }
    0 * amazonSQS.createQueue(_)
    thrown(QueueDoesNotExistException)
  }

  def "ensureQueueExists does not create queue if it exists"() {
    when:
    def queueId = PubSubUtils.ensureQueueExists(amazonSQS, queueARN, topicARN, 1)

    then:
    queueId == "my-queue-url"
    1 * amazonSQS.getQueueUrl({ GetQueueUrlRequest req ->
      req.queueName == queueARN.name && req.queueOwnerAWSAccountId == queueARN.account
    }) >> { new GetQueueUrlResult().withQueueUrl("my-queue-url") }
    0 * amazonSQS.createQueue(_)
    1 * amazonSQS.setQueueAttributes("my-queue-url", [
      "Policy": PubSubUtils.buildSQSPolicy(queueARN, topicARN).toJson(),
      "MessageRetentionPeriod": "1"
    ])
    0 * _
  }

  def "ensureQueueExists falls back to createQueue when queue is missing"() {
    when:
    def queueId = PubSubUtils.ensureQueueExists(amazonSQS, queueARN, topicARN, 1)

    then:
    queueId == "my-queue-url"
    // retry may re-invoke getQueueUrl before giving up and returning to ensureQueueExists
    (1.._) * amazonSQS.getQueueUrl(_ as GetQueueUrlRequest) >> { throw new QueueDoesNotExistException("nope") }
    1 * amazonSQS.createQueue(queueARN.name) >> { new CreateQueueResult().withQueueUrl("my-queue-url") }
    1 * amazonSQS.setQueueAttributes("my-queue-url", [
      "Policy": PubSubUtils.buildSQSPolicy(queueARN, topicARN).toJson(),
      "MessageRetentionPeriod": "1"
    ])
    0 * _
  }
}
