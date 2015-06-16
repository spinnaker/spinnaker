/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
import com.amazonaws.services.cloudwatch.model.ComparisonOperator
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.amazonaws.services.cloudwatch.model.Statistic
import com.amazonaws.services.sns.model.ListTopicsRequest
import com.amazonaws.services.sns.model.Topic
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertScalingPolicyDescription
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertScalingPolicyDescription.ScaleStrategy
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertScalingPolicyAtomicOperation implements AtomicOperation<Map<String, String>> {
  private static final Integer DEFAULT_COOLDOWN_SECONDS = 600

  UpsertScalingPolicyDescription description

  UpsertScalingPolicyAtomicOperation(UpsertScalingPolicyDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Map<String, String> operate(List priorOutputs) {

    // scaling policy
    def policyName = "${description.name}--${description.asgName}-${description.threshold}"
    def scalingPolicyRequest = new PutScalingPolicyRequest()
      .withPolicyName(policyName)
      .withScalingAdjustment(description.scaleAmount)
      .withAdjustmentType(description.scalingStrategy == ScaleStrategy.exact ? 'ChangeInCapacity' : 'PercentChangeInCapacity')
      .withCooldown(DEFAULT_COOLDOWN_SECONDS)
      .withAutoScalingGroupName(description.asgName)

    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region, true)
    PutScalingPolicyResult scalingPolicyResult = autoScaling.putScalingPolicy(scalingPolicyRequest)

    // up & down alarms
    def scaleUpAlarmReq = createAlarmRequest(scalingPolicyResult.policyARN)
    def scaleDownAlarmReq = createAlarmRequest(scalingPolicyResult.policyARN, true)

    def cloudWatch = amazonClientProvider.getCloudWatch(description.credentials, description.region, true)
    cloudWatch.putMetricAlarm(scaleUpAlarmReq)
    cloudWatch.putMetricAlarm(scaleDownAlarmReq)

    [policyName: policyName.toString()]
  }

  private PutMetricAlarmRequest createAlarmRequest(String policyArn, boolean scaleDown = Boolean.FALSE) {
    def req = new PutMetricAlarmRequest()
      .withAlarmName("${description.name}-${scaleDown ? 'scaleDown' : 'scaleUp'}--${description.asgName}-${description.threshold}")
      .withComparisonOperator(scaleDown ? ComparisonOperator.LessThanOrEqualToThreshold : ComparisonOperator.GreaterThanOrEqualToThreshold)
      .withThreshold(description.threshold)
      .withPeriod(description.period)
      .withEvaluationPeriods(description.numPeriods)
      .withStatistic(Statistic.Average)
      .withActionsEnabled(true)
      .withAlarmActions(policyArn)
      .withMetricName(description.metric.name)
      .withNamespace(description.metric.namespace)
      .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(description.asgName))

    def sns
    if (scaleDown && description.scaleDownTopic) {
      sns = description.scaleDownTopic
    } else if (!scaleDown && description.scaleUpTopic) {
      sns = description.scaleUpTopic
    }

    if (sns) {
      def topic = getTopic(sns)
      if (topic) {
        req.withAlarmActions(topic.topicArn)
      }
    }

    req
  }

  private Topic getTopic(String name) {
    if (!name.startsWith('arn:aws:sns:')) {
      name = "arn:aws:sns:${name}"
    }
    def sns = amazonClientProvider.getAmazonSNS(description.credentials, description.region, true)
    def request = new ListTopicsRequest()
    def result = sns.listTopics(request)
    while (true) {
      def topic = result.topics?.find { it.topicArn == name }
      if (topic) {
        return topic
      }
      if (!result.nextToken) {
        return null
      } else {
        result = sns.listTopics(request.withNextToken(result.nextToken))
      }
    }
  }

}
