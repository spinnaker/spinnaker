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

package com.netflix.spinnaker.kork.aws.pubsub

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueResult
import com.netflix.spinnaker.kork.aws.ARN
import spock.lang.Specification
import spock.lang.Subject

class PubSubUtilsSpec extends Specification {

  AmazonSNS amazonSNS = Mock()
  AmazonSQS amazonSQS = Mock()

  ARN queueARN = new ARN("arn:aws:sqs:us-west-2:100:queueName")
  ARN topicARN = new ARN("arn:aws:sns:us-west-2:100:topicName")

  def "should create queue if it does not exist"() {
    when:
    def queueId = PubSubUtils.ensureQueueExists(amazonSQS, queueARN, topicARN, 1)

    then:
    queueId == "my-queue-url"

    1 * amazonSQS.createQueue(queueARN.name) >> { new CreateQueueResult().withQueueUrl("my-queue-url") }
    1 * amazonSQS.setQueueAttributes("my-queue-url", [
      "Policy": PubSubUtils.buildSQSPolicy(queueARN, topicARN).toJson(),
      "MessageRetentionPeriod": "1"
    ])
    0 * _
  }

}
