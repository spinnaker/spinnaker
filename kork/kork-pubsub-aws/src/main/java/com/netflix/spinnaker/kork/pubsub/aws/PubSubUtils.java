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

package com.netflix.spinnaker.kork.pubsub.aws;

import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.SNSActions;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties.AmazonPubsubSubscription;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utils for working with AWS SNS and SQS across services */
public class PubSubUtils {
  private static final Logger log = LoggerFactory.getLogger(PubSubUtils.class);
  private static final RetrySupport retrySupport = new RetrySupport();
  private static final int MAX_RETRIES = 5;
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);
  private static final boolean EXPONENTIAL = true;

  private static String getQueueUrl(AmazonSQS amazonSQS, ARN queueARN) {
    String queueUrl;

    try {
      queueUrl = amazonSQS.getQueueUrl(queueARN.getName()).getQueueUrl();
      log.debug("Reusing existing queue {}", queueUrl);
    } catch (QueueDoesNotExistException e) {
      queueUrl = amazonSQS.createQueue(queueARN.getName()).getQueueUrl();
      log.debug("Created queue {}", queueUrl);
    }

    return queueUrl;
  }

  public static String ensureQueueExists(
      AmazonSQS amazonSQS, ARN queueARN, ARN topicARN, int sqsMessageRetentionPeriodSeconds) {
    String queueUrl =
        retrySupport.retry(
            () -> getQueueUrl(amazonSQS, queueARN), MAX_RETRIES, RETRY_BACKOFF, EXPONENTIAL);

    HashMap<String, String> attributes = new HashMap<>();
    attributes.put("Policy", buildSQSPolicy(queueARN, topicARN).toJson());
    attributes.put("MessageRetentionPeriod", Integer.toString(sqsMessageRetentionPeriodSeconds));
    amazonSQS.setQueueAttributes(queueUrl, attributes);

    return queueUrl;
  }

  /** Returns the subscription arn resulting from subscribing the queueARN to the topicARN */
  public static String subscribeToTopic(AmazonSNS amazonSNS, ARN topicARN, ARN queueARN) {
    return retrySupport.retry(
        () -> amazonSNS.subscribe(topicARN.getArn(), "sqs", queueARN.getArn()).getSubscriptionArn(),
        MAX_RETRIES,
        RETRY_BACKOFF,
        EXPONENTIAL);
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

  /**
   * Ensure that the topic exists and has a policy granting the specified accounts permission to
   * publish messages to it
   */
  public static String ensureTopicExists(
      AmazonSNS amazonSNS, ARN topicARN, AmazonPubsubSubscription subscription) {
    String createdTopicARN =
        retrySupport.retry(
            () -> amazonSNS.createTopic(topicARN.getName()).getTopicArn(),
            MAX_RETRIES,
            RETRY_BACKOFF,
            EXPONENTIAL);

    log.debug(
        (createdTopicARN.equals(topicARN.getArn()))
            ? "Reusing existing topic {}"
            : "Created topic {}",
        createdTopicARN);

    if (!subscription.getAccountIds().isEmpty()) {
      amazonSNS.setTopicAttributes(
          new SetTopicAttributesRequest()
              .withTopicArn(createdTopicARN)
              .withAttributeName("Policy")
              .withAttributeValue(
                  buildSNSPolicy(new ARN(createdTopicARN), subscription.getAccountIds()).toJson()));
    }

    return createdTopicARN;
  }

  public static Supplier<Boolean> getEnabledSupplier(
      DynamicConfigService dynamicConfig,
      AmazonPubsubSubscription subscription,
      DiscoveryStatusListener discoveryStatus) {
    return () ->
        dynamicConfig.isEnabled("pubsub", false)
            && dynamicConfig.isEnabled("pubsub.amazon", false)
            && dynamicConfig.isEnabled("pubsub.amazon." + subscription.getName(), false)
            && discoveryStatus.isEnabled();
  }

  public static Policy buildSNSPolicy(ARN topicARN, List<String> accountIds) {
    Statement statement = new Statement(Statement.Effect.Allow).withActions(SNSActions.Publish);
    statement.setPrincipals(accountIds.stream().map(Principal::new).collect(Collectors.toList()));
    statement.setResources(Collections.singletonList(new Resource(topicARN.getArn())));

    return new Policy("allow-remote-account-send", Collections.singletonList(statement));
  }
}
