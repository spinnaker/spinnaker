/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.CreateTopicRequest
import software.amazon.awssdk.services.sns.model.CreateTopicResponse
import software.amazon.awssdk.services.sns.model.SetTopicAttributesRequest
import software.amazon.awssdk.services.sns.model.SubscribeRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest
import spock.lang.Specification

class LaunchFailureNotificationAgentSpec extends Specification {
  def mgmtCredentials = Mock(NetflixAmazonCredentials) {
    getAccountId() >> { return "100" }
    getName() >> { return "mgmt" }
  }

  def snsClient = Mock(SnsClient)
  def sqsClient = Mock(SqsClient)

  def queueARN = new ARN([mgmtCredentials], "arn:aws:sqs:us-west-2:100:queueName")
  def topicARN = new ARN([mgmtCredentials], "arn:aws:sns:us-west-2:100:topicName")
  def allAccountIds = [topicARN.account.accountId, queueARN.account.accountId].unique()

  void "should create topic if it does not exist"() {
    when:
    def topicId = LaunchFailureNotificationAgent.ensureTopicExists(snsClient, topicARN, allAccountIds, queueARN)

    then:
    topicId == topicARN.arn

    1 * snsClient.createTopic(_ as CreateTopicRequest) >> { CreateTopicRequest request ->
      assert request.name() == topicARN.name
      CreateTopicResponse.builder().topicArn(topicARN.arn).build()
    }

    // should attach a policy granting SendMessage rights to the source topic
    1 * snsClient.setTopicAttributes(_ as SetTopicAttributesRequest) >> { SetTopicAttributesRequest request ->
      assert request.topicArn() == topicARN.arn
      assert request.attributeName() == "Policy"
      assert request.attributeValue() == LaunchFailureNotificationAgent.buildSNSPolicy(topicARN, allAccountIds).toJson()
      null
    }

    // should subscribe the queue to this topic
    1 * snsClient.subscribe(_ as SubscribeRequest) >> { SubscribeRequest request ->
      assert request.topicArn() == topicARN.arn
      assert request.protocol() == "sqs"
      assert request.endpoint() == queueARN.arn
      null
    }
    0 * _
  }

  void "should create queue if it does not exist"() {
    when:
    def queueId = LaunchFailureNotificationAgent.ensureQueueExists(sqsClient, queueARN, topicARN)

    then:
    queueId == "my-queue-url"

    1 * sqsClient.getQueueUrl(_ as GetQueueUrlRequest) >> {
      throw QueueDoesNotExistException.builder().message("This queue does not exist").build()
    }
    1 * sqsClient.createQueue(_ as CreateQueueRequest) >> { CreateQueueRequest request ->
      assert request.queueName() == queueARN.name
      CreateQueueResponse.builder().queueUrl("my-queue-url").build()
    }

    // should attach a policy granting SendMessage rights to the source topic
    1 * sqsClient.setQueueAttributes(_ as SetQueueAttributesRequest) >> { SetQueueAttributesRequest request ->
      assert request.queueUrl() == "my-queue-url"
      null
    }
    0 * _
  }

  void "should delegate to ServerGroupTagger w/ status message, accountId and region"() {
    given:
    def serverGroupTagger = Mock(EntityTagger)
    def notificationMessage = new NotificationMessage(
      autoScalingGroupARN: "arn:aws:autoscaling:us-west-2:100:serverGroupName",
      autoScalingGroupName: "serverGroupName",
      event: "MY_EVENT",
      statusMessage: "My Status Message"
    )

    when:
    LaunchFailureNotificationAgent.handleMessage(serverGroupTagger, notificationMessage)

    then:
    1 * serverGroupTagger.alert("aws", "100", "us-west-2", null, "servergroup", "serverGroupName", "MY_EVENT", "My Status Message", null)

    when:
    LaunchFailureNotificationAgent.handleMessage(
      serverGroupTagger, new NotificationMessage(autoScalingGroupARN: "invalid:arn")
    )

    then:
    thrown(IllegalArgumentException)
  }
}
