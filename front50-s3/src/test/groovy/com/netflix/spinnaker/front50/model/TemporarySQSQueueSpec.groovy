/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.ListTopicsResult
import com.amazonaws.services.sns.model.SubscribeResult
import com.amazonaws.services.sns.model.Topic
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.CreateQueueResult
import spock.lang.Specification;

class TemporarySQSQueueSpec extends Specification {
  def "should create and teardown temporary sqs queue"() {
    given:
    def amazonSQS = Mock(AmazonSQS)
    def amazonSNS = Mock(AmazonSNS)

    when:
    def temporarySQSQueue = new TemporarySQSQueue(amazonSQS, amazonSNS, "my_topic", "my_id")

    then:
    1 * amazonSNS.listTopics() >> {
      return new ListTopicsResult().withTopics(
        new Topic().withTopicArn("arn:aws:sns:us-west-2:123:not_my_topic"),
        new Topic().withTopicArn("arn:aws:sns:us-west-2:123:my_topic")
      )
    }
    1 * amazonSQS.createQueue({ CreateQueueRequest cqr ->
      cqr.attributes["MessageRetentionPeriod"] == "60" && cqr.queueName == "my_topic__my_id"
    }) >> {
      new CreateQueueResult().withQueueUrl("http://my/queue_url")
    }
    1 * amazonSNS.subscribe("arn:aws:sns:us-west-2:123:my_topic", "sqs", "arn:aws:sqs:us-west-2:123:my_topic__my_id") >> {
      new SubscribeResult().withSubscriptionArn("arn:subscription")
    }
    1 * amazonSQS.setQueueAttributes("http://my/queue_url", _)
    0 * _

    temporarySQSQueue.temporaryQueue.with {
      snsTopicArn == "arn:aws:sns:us-west-2:1234:my_topic"
      sqsQueueArn == "arn:aws:sqs:us-west-2:1234:my_topic__my_id"
      sqsQueueUrl == "http://my/queue_url"
      snsTopicSubscriptionArn == "arn:subscription"
    }

    when:
    temporarySQSQueue.shutdown()

    then:
    1 * amazonSQS.deleteQueue("http://my/queue_url")
    1 * amazonSNS.unsubscribe("arn:subscription")
    0 * _
  }
}
