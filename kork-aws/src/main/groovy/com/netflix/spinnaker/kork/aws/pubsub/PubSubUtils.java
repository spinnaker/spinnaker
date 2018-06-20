package com.netflix.spinnaker.kork.aws.pubsub;

import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.netflix.spinnaker.kork.aws.ARN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;

/**
 * Utils for working with AWS SNS and SQS across services
 */
public class PubSubUtils {
  private static final Logger log = LoggerFactory.getLogger(PubSubUtils.class);

  public static String ensureQueueExists(AmazonSQS amazonSQS,
                                  ARN queueARN,
                                  ARN topicARN,
                                  int sqsMessageRetentionPeriodSeconds) {
    String queueUrl = amazonSQS.createQueue(queueARN.getName()).getQueueUrl();
    log.debug("Created queue " + queueUrl);

    HashMap<String, String> attributes = new HashMap<>();
    attributes.put("Policy", buildSQSPolicy(queueARN, topicARN).toJson());
    attributes.put("MessageRetentionPeriod", Integer.toString(sqsMessageRetentionPeriodSeconds));
    amazonSQS.setQueueAttributes(
      queueUrl,
      attributes
    );

    return queueUrl;
  }

  public static String subscribeToTopic(AmazonSNS amazonSNS, ARN topicARN, ARN queueARN) {
    amazonSNS.subscribe(topicARN.getArn(), "sqs", queueARN.getArn());
    return topicARN.getArn();
  }

  /**
   * This policy allows messages to be sent from an SNS topic.
   */
  public static Policy buildSQSPolicy(ARN queue, ARN topic) {
    Statement snsStatement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    snsStatement.setPrincipals(Principal.All);
    snsStatement.setResources(Collections.singletonList(new Resource(queue.getArn())));
    snsStatement.setConditions(Collections.singletonList(
      new Condition().withType("ArnEquals").withConditionKey("aws:SourceArn").withValues(topic.getArn())
    ));

    return new Policy("allow-sns-send", Collections.singletonList(snsStatement));
  }
}
