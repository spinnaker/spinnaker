package com.netflix.spinnaker.clouddriver.aws.lifecycle

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.CreateTopicResult
import com.amazonaws.services.sns.model.SetTopicAttributesRequest
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueResult
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.tags.ServerGroupTagger
import spock.lang.Specification
import spock.lang.Unroll

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

class LaunchFailureNotificationAgentSpec extends Specification {
  def mgmtCredentials = Mock(NetflixAmazonCredentials) {
    getAccountId() >> { return "100" }
    getName() >> { return "mgmt" }
  }

  def amazonSNS = Mock(AmazonSNS)
  def amazonSQS = Mock(AmazonSQS)

  def queueARN = new LaunchFailureNotificationAgent.ARN([mgmtCredentials], "arn:aws:sqs:us-west-2:100:queueName")
  def topicARN = new LaunchFailureNotificationAgent.ARN([mgmtCredentials], "arn:aws:sns:us-west-2:100:topicName")
  def allAccountIds = [topicARN.account.accountId, queueARN.account.accountId].unique()

  void "should create topic if it does not exist"() {
    when:
    def topicId = LaunchFailureNotificationAgent.ensureTopicExists(amazonSNS, topicARN, allAccountIds, queueARN)

    then:
    topicId == topicARN.arn

    1 * amazonSNS.createTopic(topicARN.name) >> { new CreateTopicResult().withTopicArn(topicARN.arn) }

    // should attach a policy granting SendMessage rights to the source topic
    1 * amazonSNS.setTopicAttributes(new SetTopicAttributesRequest()
      .withTopicArn(topicARN.arn)
      .withAttributeName("Policy")
      .withAttributeValue(LaunchFailureNotificationAgent.buildSNSPolicy(topicARN, allAccountIds).toJson()))

    // should subscribe the queue to this topic
    1 * amazonSNS.subscribe(topicARN.arn, "sqs", queueARN.arn)
    0 * _
  }

  void "should create queue if it does not exist"() {
    when:
    def queueId = LaunchFailureNotificationAgent.ensureQueueExists(amazonSQS, queueARN, topicARN)

    then:
    queueId == "my-queue-url"

    1 * amazonSQS.getQueueUrl(_) >> { throw new QueueDoesNotExistException("This queue does not exist")}
    1 * amazonSQS.createQueue(queueARN.name) >> { new CreateQueueResult().withQueueUrl("my-queue-url") }

    // should attach a policy granting SendMessage rights to the source topic
    1 * amazonSQS.setQueueAttributes("my-queue-url", [
        "Policy": LaunchFailureNotificationAgent.buildSQSPolicy(queueARN, topicARN).toJson()
    ])
    0 * _
  }

  @Unroll
  void "should extract accountId, region and name from SQS or SNS ARN"() {
    when:
    def parsedARN = new LaunchFailureNotificationAgent.ARN(
      [mgmtCredentials],
      arn,
    )

    then:
    parsedARN.account == mgmtCredentials
    parsedARN.region == expectedRegion
    parsedARN.name == expectedName

    when:
    new LaunchFailureNotificationAgent.ARN([mgmtCredentials], "invalid-arn")

    then:
    def e1 = thrown(IllegalArgumentException)
    e1.message == "invalid-arn is not a valid SNS or SQS ARN"

    when:
    new LaunchFailureNotificationAgent.ARN([], arn)

    then:
    def e2 = thrown(IllegalArgumentException)
    e2.message == "No account credentials found for 100"

    where:
    arn                                   || expectedRegion || expectedName
    "arn:aws:sqs:us-west-2:100:queueName" || "us-west-2"    || "queueName"
    "arn:aws:sns:us-west-2:100:topicName" || "us-west-2"    || "topicName"
  }

  void "should delegate to ServerGroupTagger w/ status message, accountId and region"() {
    given:
    def serverGroupTagger = Mock(ServerGroupTagger)
    def notificationMessage = new NotificationMessage(
      autoScalingGroupARN: "arn:aws:autoscaling:us-west-2:100:serverGroupName",
      autoScalingGroupName: "serverGroupName",
      event: "MY_EVENT",
      statusMessage: "My Status Message"
    )

    when:
    LaunchFailureNotificationAgent.handleMessage(serverGroupTagger, notificationMessage)

    then:
    1 * serverGroupTagger.alert("aws", "100", "us-west-2", "serverGroupName", "MY_EVENT", "My Status Message")

    when:
    LaunchFailureNotificationAgent.handleMessage(
      serverGroupTagger, new NotificationMessage(autoScalingGroupARN: "invalid:arn")
    )

    then:
    thrown(IllegalArgumentException)
  }
}
