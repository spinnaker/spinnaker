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
import com.amazonaws.services.sns.model.SubscribeResult
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.ReceiveMessageResult
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties
import spock.lang.Specification

import java.util.function.Supplier

class SQSSubscriberSpec extends Specification {
  def "acknowledger acknowledges when handler succeeds"() {
    given:
    def messageAcknowledger = Mock(AmazonMessageAcknowledger)

    SQSSubscriber subscriber = new SQSSubscriber(
      subscription(),
      Mock(AmazonPubsubMessageHandler),
      messageAcknowledger,
      amazonSNS(),
      amazonSQS(),
      enabledOnce(),
      new DefaultRegistry()
    )

    when:
    subscriber.initializeQueue()
    subscriber.listenForMessages()

    then:
    1 * messageAcknowledger.ack(_, _)
    0 * messageAcknowledger.nack(_, _)
  }

  def "acknowledger nacks when handler fails"() {
    given:
    def messageAcknowledger = Mock(AmazonMessageAcknowledger)
    def throwyHandler = Stub(AmazonPubsubMessageHandler) {
      handleMessage(_) >> { throw new RuntimeException("unhappy handler") }
    }

    SQSSubscriber subscriber = new SQSSubscriber(
      subscription(),
      throwyHandler,
      messageAcknowledger,
      amazonSNS(),
      amazonSQS(),
      enabledOnce(),
      new DefaultRegistry()
    )

    when:
    subscriber.initializeQueue()
    subscriber.listenForMessages()

    then:
    0 * messageAcknowledger.ack(_, _)
    1 * messageAcknowledger.nack(_, _)
  }

  def "the subscriber does not query SQS when disabled"() {
    given:
    def amazonSQS = Mock(AmazonSQS)
    def disabled = Stub(Supplier) {
      get() >> false
    }

    SQSSubscriber subscriber = new SQSSubscriber(
      subscription(),
      Mock(AmazonPubsubMessageHandler),
      Mock(AmazonMessageAcknowledger),
      amazonSNS(),
      amazonSQS,
      disabled,
      new DefaultRegistry()
    )

    when:
    subscriber.listenForMessages()

    then:
    0 * amazonSQS.receiveMessage(_)
  }


  def subscription() {
    def subscription = new AmazonPubsubProperties.AmazonPubsubSubscription()
    subscription.name = "name"
    subscription.topicARN = "arn:aws:sns:us-east-2:123456789012:MyTopic"
    subscription.queueARN = "arn:aws:sqs:us-east-2:123456789012:MyQueue"
    return subscription
  }

  def amazonSQS() {
    def getQueueUrlResult = Stub(GetQueueUrlResult) {
      getQueueUrl() >> "https://queueUrl"
    }
    def receiveMessageResult = Stub(ReceiveMessageResult) {
      getMessages() >> [Mock(Message)]
    }

    return Stub(AmazonSQS) {
      getQueueUrl(_) >> getQueueUrlResult
      receiveMessage(_) >> receiveMessageResult
    }
  }

  def amazonSNS() {
    def subscribeResult = Stub(SubscribeResult) {
      getSubscriptionArn() >> "arn:aws:sqs:us-east-2:123456789012:MySubscription"
    }
    return Stub(AmazonSNS) {
      subscribe(_) >> subscribeResult
    }
  }

  def enabledOnce() {
    return Stub(Supplier) {
      get() >>> [true, false]
    }
  }
}
