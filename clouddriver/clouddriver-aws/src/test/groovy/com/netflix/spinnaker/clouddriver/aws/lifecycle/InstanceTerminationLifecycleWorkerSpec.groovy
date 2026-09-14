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
package com.netflix.spinnaker.clouddriver.aws.lifecycle

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import okhttp3.Request
import retrofit2.mock.Calls
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.CreateTopicRequest
import software.amazon.awssdk.services.sns.model.CreateTopicResponse
import software.amazon.awssdk.services.sns.model.SetTopicAttributesRequest
import software.amazon.awssdk.services.sns.model.SubscribeRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import jakarta.inject.Provider

class InstanceTerminationLifecycleWorkerSpec extends Specification {

  NetflixAmazonCredentials mgmtCredentials = Mock() {
    getAccountId() >> { return "100" }
    getName() >> { return "mgmt" }
  }
  NetflixAmazonCredentials testCredentials = Mock() {
    getAccountId() >> { return "200" }
    getName() >> { return "test" }
  }

  SqsClient sqsClient = Mock()
  SnsClient snsClient = Mock()
  CredentialsRepository credentialsRepository = Mock(CredentialsRepository) {
    getAll() >>[mgmtCredentials, testCredentials]
  }
  Provider<AwsEurekaSupport> awsEurekaSupportProvider = Mock()
  AwsEurekaSupport awsEurekaSupport = Mock()
  Eureka eureka = Mock()
  Registry registry = Mock()

  def queueARN = new ARN([mgmtCredentials, testCredentials], "arn:aws:sqs:us-west-2:100:queueName")
  def topicARN = new ARN([mgmtCredentials, testCredentials], "arn:aws:sns:us-west-2:100:topicName")

  @Shared
  def objectMapper = new ObjectMapper()

  @Subject
  def subject = new InstanceTerminationLifecycleWorker(
    objectMapper,
    Mock(AmazonClientProvider),
    credentialsRepository,
    new InstanceTerminationConfigurationProperties(
      'mgmt',
      queueARN.arn,
      topicARN.arn,
      -1,
      -1,
      -1,
      3
    ),
    awsEurekaSupportProvider,
    registry
  )

  def "should create topic if it does not exist"() {
    when:
    def topicId = LaunchFailureNotificationAgent.ensureTopicExists(snsClient, topicARN, ['100', '200'], queueARN)

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
      assert request.attributeValue() == LaunchFailureNotificationAgent.buildSNSPolicy(topicARN, ['100', '200']).toJson()
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

  def 'should create queue if it does not exist'() {
    when:
    def queueId = InstanceTerminationLifecycleWorker.ensureQueueExists(sqsClient, queueARN, topicARN, [] as Set<String>, 1)

    then:
    queueId == "my-queue-url"

    1 * sqsClient.createQueue(_ as CreateQueueRequest) >> { CreateQueueRequest request ->
      assert request.queueName() == queueARN.name
      CreateQueueResponse.builder().queueUrl("my-queue-url").build()
    }

    1 * sqsClient.setQueueAttributes(_ as SetQueueAttributesRequest) >> { SetQueueAttributesRequest request ->
      assert request.queueUrl() == "my-queue-url"
      null
    }
    0 * _
  }

  def 'should get update discovery with notification'() {
    given:
    LifecycleMessage message = new LifecycleMessage(
      accountId: '200',
      autoScalingGroupName: 'clouddriver-main-v000',
      ec2InstanceId: 'i-1234',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING'
    )

    when:
    subject.handleMessage(message)

    then:
    1 * credentialsRepository.getAll() >> [mgmtCredentials, testCredentials]
    1 * awsEurekaSupportProvider.get() >> awsEurekaSupport
    1 * awsEurekaSupport.getEureka(_, 'us-west-2') >> eureka
    1 * eureka.updateInstanceStatus('clouddriver', 'i-1234', DiscoveryStatus.OUT_OF_SERVICE.value) >> Calls.response(null)
  }

  def 'should process both sns and sqs messages'() {
    given:
    LifecycleMessage lifecycleMessage = new LifecycleMessage(
      accountId: '1234',
      autoScalingGroupName: 'clouddriver-main-v000',
      ec2InstanceId: 'i-1324',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING'
    )
    String sqsMessage = objectMapper.writeValueAsString(lifecycleMessage)
    String snsMessage = objectMapper.writeValueAsString(new NotificationMessageWrapper(
      subject: 'lifecycle message',
      message: sqsMessage
    ))

    when:
    LifecycleMessage result = subject.unmarshalLifecycleMessage(snsMessage)

    then:
    result.accountId == lifecycleMessage.accountId
    result.autoScalingGroupName == lifecycleMessage.autoScalingGroupName
    result.ec2InstanceId == lifecycleMessage.ec2InstanceId
    result.lifecycleTransition == lifecycleMessage.lifecycleTransition

    when:
    result = subject.unmarshalLifecycleMessage(sqsMessage)

    then:
    result.accountId == lifecycleMessage.accountId
    result.autoScalingGroupName == lifecycleMessage.autoScalingGroupName
    result.ec2InstanceId == lifecycleMessage.ec2InstanceId
    result.lifecycleTransition == lifecycleMessage.lifecycleTransition
  }

  def 'should build sqs policy supporting both sns and direct notifications'() {
    given:
    Set<String> terminatingRoleArns = ['arn:aws:iam::100:role/terminatingRole', 'arn:aws:iam::200:role/terminatingRole']

    when:
    def result = InstanceTerminationLifecycleWorker.buildSQSPolicy(queueARN, topicARN, terminatingRoleArns)

    then:
    def json = result.toJson()
    json.contains("allow-sns-or-sqs-send")
    json.contains("sqs:SendMessage")
    json.contains("sqs:GetQueueUrl")
    json.contains("arn:aws:sqs:us-west-2:100:queueName")
    json.contains("arn:aws:sns:us-west-2:100:topicName")
  }

  def 'should retry on network errors'() {
    given:
    subject.registry.counter(_) >> Mock(Counter)

    when:
    subject.updateInstanceStatus(eureka, 'foo', 'i-1234')

    then:
    1 * eureka.updateInstanceStatus(_, _, _) >> {
      throw new SpinnakerNetworkException(new IOException("timeout"), new Request.Builder().url("http://some-url").build())
    }
    1 * eureka.updateInstanceStatus(_, _, _)
    0 * eureka.updateInstanceStatus(_, _, _)
  }
}
