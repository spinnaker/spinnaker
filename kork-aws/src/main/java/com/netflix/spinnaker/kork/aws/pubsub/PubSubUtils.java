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

package com.netflix.spinnaker.kork.aws.pubsub;

import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.netflix.spinnaker.kork.aws.ARN;
import java.util.Collections;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utils for working with AWS SNS and SQS across services */
public class PubSubUtils {
  private static final Logger log = LoggerFactory.getLogger(PubSubUtils.class);

  public static String ensureQueueExists(
      AmazonSQS amazonSQS, ARN queueARN, ARN topicARN, int sqsMessageRetentionPeriodSeconds) {
    String queueUrl = amazonSQS.createQueue(queueARN.getName()).getQueueUrl();
    log.debug("Created queue " + queueUrl);

    HashMap<String, String> attributes = new HashMap<>();
    attributes.put("Policy", buildSQSPolicy(queueARN, topicARN).toJson());
    attributes.put("MessageRetentionPeriod", Integer.toString(sqsMessageRetentionPeriodSeconds));
    amazonSQS.setQueueAttributes(queueUrl, attributes);

    return queueUrl;
  }

  /** Returns the subscription arn resulting from subscribing the queueARN to the topicARN */
  public static String subscribeToTopic(AmazonSNS amazonSNS, ARN topicARN, ARN queueARN) {
    return amazonSNS.subscribe(topicARN.getArn(), "sqs", queueARN.getArn()).getSubscriptionArn();
  }

  /** This policy allows messages to be sent from an SNS topic. */
  public static Policy buildSQSPolicy(ARN queue, ARN topic) {
    Statement snsStatement =
        new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    snsStatement.setPrincipals(Principal.All);
    snsStatement.setResources(Collections.singletonList(new Resource(queue.getArn())));
    snsStatement.setConditions(
        Collections.singletonList(
            new Condition()
                .withType("ArnEquals")
                .withConditionKey("aws:SourceArn")
                .withValues(topic.getArn())));

    return new Policy("allow-sns-send", Collections.singletonList(snsStatement));
  }
}
