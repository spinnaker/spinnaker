/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesRequest
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesResponse
import software.amazon.awssdk.services.autoscaling.model.MetricDimension
import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyRequest
import software.amazon.awssdk.services.autoscaling.model.ScalingPolicy
import software.amazon.awssdk.services.autoscaling.model.TargetTrackingConfiguration
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest
import com.google.common.collect.Lists
import com.netflix.spinnaker.clouddriver.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DefaultScalingPolicyCopier implements ScalingPolicyCopier {

  public static final DIMENSION_NAME_FOR_ASG = 'AutoScalingGroupName'

  AmazonClientProvider amazonClientProvider

  IdGenerator idGenerator

  PolicyNameGenerator policyNameGenerator

  @Autowired
  DefaultScalingPolicyCopier(AmazonClientProvider amazonClientProvider, IdGenerator idGenerator) {
    this.amazonClientProvider = amazonClientProvider
    this.idGenerator = idGenerator
    this.policyNameGenerator = new PolicyNameGenerator(idGenerator, amazonClientProvider)
  }

  DefaultScalingPolicyCopier(AmazonClientProvider amazonClientProvider, IdGenerator idGenerator, PolicyNameGenerator policyNameGenerator) {
    this.amazonClientProvider = amazonClientProvider
    this.idGenerator = idGenerator
    this.policyNameGenerator = policyNameGenerator
  }

  @Override
  void copyScalingPolicies(Task task,
                           String sourceAsgName,
                           String targetAsgName,
                           NetflixAmazonCredentials sourceCredentials,
                           NetflixAmazonCredentials targetCredentials,
                           String sourceRegion,
                           String targetRegion) {
    AutoScalingClient sourceAutoScaling = amazonClientProvider.getAutoScalingV2(sourceCredentials, sourceRegion)
    AutoScalingClient targetAutoScaling = amazonClientProvider.getAutoScalingV2(targetCredentials, targetRegion)
    List<ScalingPolicy> sourceAsgScalingPolicies = new ScalingPolicyRetriever(sourceAutoScaling).retrieve(DescribePoliciesRequest.builder().autoScalingGroupName(sourceAsgName).build())

    log.info("Copying scaling policies for $sourceAsgName to $targetAsgName: $sourceAsgScalingPolicies")

    Map<String, String> sourcePolicyArnToTargetPolicyArn = [:]
    sourceAsgScalingPolicies.each { sourceAsgScalingPolicy ->
      String newPolicyName = policyNameGenerator.generateScalingPolicyName(sourceCredentials, sourceRegion, sourceAsgName, targetAsgName, sourceAsgScalingPolicy)
      def policyRequest = buildNewPolicyRequest(newPolicyName, sourceAsgScalingPolicy, targetAsgName)
      task.updateStatus "AWS_DEPLOY", "Creating scaling policy (${policyRequest}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."

      def result = targetAutoScaling.putScalingPolicy(policyRequest)
      sourcePolicyArnToTargetPolicyArn[sourceAsgScalingPolicy.policyARN()] = result.policyARN()

      task.updateStatus "AWS_DEPLOY", "Created scaling policy (${policyRequest}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."
    }
    Collection<String> allSourceAlarmNames = sourceAsgScalingPolicies.collectMany { it.alarms() }*.alarmName().unique()
    if (allSourceAlarmNames) {
      copyAlarmsForAsg(targetAsgName, allSourceAlarmNames, sourcePolicyArnToTargetPolicyArn, sourceCredentials, targetCredentials, sourceRegion, targetRegion)
    }
  }

  protected PutScalingPolicyRequest buildNewPolicyRequest(String newPolicyName, ScalingPolicy sourceAsgScalingPolicy, String targetAsgName) {
    TargetTrackingConfiguration targetTrackingConfiguration = sourceAsgScalingPolicy.targetTrackingConfiguration()
    if (targetTrackingConfiguration) {
      if (targetTrackingConfiguration.customizedMetricSpecification()) {
        // update target tracking policies to point to the new ASG
        // this will cause grief if a target tracking policy is configured against a *different* ASG, but we are doing
        // the same thing with simple and step policies and have not had any issues thus far
        List<MetricDimension> newDimensions = targetTrackingConfiguration.customizedMetricSpecification().dimensions().collect { d ->
          d.name() == DIMENSION_NAME_FOR_ASG ? d.toBuilder().value(targetAsgName).build() : d
        }
        targetTrackingConfiguration = targetTrackingConfiguration.toBuilder()
          .customizedMetricSpecification(targetTrackingConfiguration.customizedMetricSpecification().toBuilder()
            .dimensions(newDimensions)
            .build())
          .build()
      }
    }
    return PutScalingPolicyRequest.builder()
      .autoScalingGroupName(targetAsgName)
      .policyName(newPolicyName)
      .policyType(sourceAsgScalingPolicy.policyType())
      .scalingAdjustment(sourceAsgScalingPolicy.scalingAdjustment())
      .adjustmentType(sourceAsgScalingPolicy.adjustmentType())
      .cooldown(sourceAsgScalingPolicy.cooldown())
      .minAdjustmentStep(sourceAsgScalingPolicy.minAdjustmentStep())
      .minAdjustmentMagnitude(sourceAsgScalingPolicy.minAdjustmentMagnitude())
      .metricAggregationType(sourceAsgScalingPolicy.metricAggregationType())
      .stepAdjustments(sourceAsgScalingPolicy.stepAdjustments())
      .estimatedInstanceWarmup(sourceAsgScalingPolicy.estimatedInstanceWarmup())
      .targetTrackingConfiguration((TargetTrackingConfiguration) targetTrackingConfiguration)
      .build()
  }

  Collection<String> replacePolicyArnActions(String sourceRegion,
                                             String targetRegion,
                                             NetflixAmazonCredentials sourceCredentials,
                                             NetflixAmazonCredentials targetCredentials,
                                             Map<String, String> replacements,
                                             Collection<String> actions) {
    replacements.each { sourcePolicyArn, targetPolicyArn ->
      if (sourcePolicyArn in actions) {
        actions = (actions - sourcePolicyArn) + targetPolicyArn
      }
    }
    // if we are copying across accounts or region, do not copy over unrelated alarms, e.g. sns queues
    if (sourceRegion != targetRegion) {
      actions = actions.findAll { !it.contains(sourceRegion) }
    }
    if (sourceCredentials.accountId != targetCredentials.accountId) {
      actions = actions.findAll { !it.contains(sourceCredentials.accountId) }
    }
    actions
  }

  private void copyAlarmsForAsg(String newAutoScalingGroupName,
                                Collection<String> sourceAlarmNames,
                                Map<String, String> sourcePolicyArnToTargetPolicyArn,
                                NetflixAmazonCredentials sourceCredentials,
                                NetflixAmazonCredentials targetCredentials,
                                String sourceRegion,
                                String targetRegion) {
    CloudWatchClient sourceCloudWatch = amazonClientProvider.getAmazonCloudWatchV2(sourceCredentials, sourceRegion)
    CloudWatchClient targetCloudWatch = amazonClientProvider.getAmazonCloudWatchV2(targetCredentials, targetRegion)
    List<MetricAlarm> sourceAlarms = new AlarmRetriever(sourceCloudWatch).retrieve(DescribeAlarmsRequest.builder().alarmNames(sourceAlarmNames).build())

    log.info("Copying scaling policy alarms for $newAutoScalingGroupName: $sourceAlarms")

    sourceAlarms.findAll { shouldCopySourceAlarm(it) }.each { alarm ->
      List<Dimension> newDimensions = Lists.newArrayList(alarm.dimensions())
      Dimension asgDimension = newDimensions.find { it.name() == DIMENSION_NAME_FOR_ASG }
      if (asgDimension) {
        newDimensions.remove(asgDimension)
        newDimensions.add(Dimension.builder().name(DIMENSION_NAME_FOR_ASG).value(newAutoScalingGroupName).build())
      }
      String newAlarmName = [newAutoScalingGroupName, 'alarm', idGenerator.nextId()].join('-')
      def request = PutMetricAlarmRequest.builder()
        .alarmName(newAlarmName)
        .alarmDescription(alarm.alarmDescription())
        .actionsEnabled(alarm.actionsEnabled())
        .okActions(replacePolicyArnActions(sourceRegion, targetRegion, sourceCredentials, targetCredentials, sourcePolicyArnToTargetPolicyArn, alarm.okActions()))
        .alarmActions(replacePolicyArnActions(sourceRegion, targetRegion, sourceCredentials, targetCredentials, sourcePolicyArnToTargetPolicyArn, alarm.alarmActions()))
        .insufficientDataActions(replacePolicyArnActions(sourceRegion, targetRegion, sourceCredentials, targetCredentials, sourcePolicyArnToTargetPolicyArn, alarm.insufficientDataActions()))
        .metricName(alarm.metricName())
        .namespace(alarm.namespace())
        .statistic(alarm.statisticAsString())
        .extendedStatistic(alarm.extendedStatistic())
        .dimensions(newDimensions)
        .period(alarm.period())
        .unit(alarm.unitAsString())
        .evaluationPeriods(alarm.evaluationPeriods())
        .threshold(alarm.threshold())
        .comparisonOperator(alarm.comparisonOperatorAsString())
        .build()
      targetCloudWatch.putMetricAlarm(request)
    }
  }

  protected boolean shouldCopySourceAlarm(MetricAlarm metricAlarm) {
    // AWS auto-creates TargetTracking alarms, so we don't want to copy them (otherwise, we'll have duplicates)
    return !metricAlarm.alarmName().startsWith("TargetTracking-")
  }

  @Canonical
  static class ScalingPolicyRetriever extends AwsResultsRetriever<ScalingPolicy, DescribePoliciesRequest, DescribePoliciesResponse> {
    final AutoScalingClient autoScaling

    @Override
    protected DescribePoliciesResponse makeRequest(DescribePoliciesRequest request) {
      autoScaling.describePolicies(request)
    }

    @Override
    protected List<ScalingPolicy> accessResult(DescribePoliciesResponse result) {
      result.scalingPolicies()
    }

    @Override
    protected DescribePoliciesRequest setNextToken(DescribePoliciesRequest request, String nextToken) {
      request.toBuilder().nextToken(nextToken).build()
    }

    @Override
    protected String getNextToken(DescribePoliciesResponse result) {
      result.nextToken()
    }
  }

  @Canonical
  static class AlarmRetriever extends AwsResultsRetriever<MetricAlarm, DescribeAlarmsRequest, DescribeAlarmsResponse> {
    final CloudWatchClient cloudWatch

    @Override
    protected DescribeAlarmsResponse makeRequest(DescribeAlarmsRequest request) {
      cloudWatch.describeAlarms(request)
    }

    @Override
    protected List<MetricAlarm> accessResult(DescribeAlarmsResponse result) {
      result.metricAlarms()
    }

    @Override
    protected DescribeAlarmsRequest setNextToken(DescribeAlarmsRequest request, String nextToken) {
      request.toBuilder().nextToken(nextToken).build()
    }

    @Override
    protected String getNextToken(DescribeAlarmsResponse result) {
      result.nextToken()
    }
  }

  static class PolicyNameGenerator {
    private IdGenerator idGenerator

    private AmazonClientProvider amazonClientProvider

    PolicyNameGenerator(IdGenerator idGenerator, AmazonClientProvider amazonClientProvider) {
      this.idGenerator = idGenerator
      this.amazonClientProvider = amazonClientProvider
    }

    String generateScalingPolicyName(NetflixAmazonCredentials sourceCredentials, String sourceRegion, String sourceAsgName, String targetAsgName, ScalingPolicy policy) {
      policy.policyName().replaceAll(sourceAsgName, targetAsgName)
      String fallback = policy.policyName().contains(sourceAsgName) ?
        policy.policyName().replaceAll(sourceAsgName, targetAsgName) :
        [policy.policyName(), 'no-alarm', idGenerator.nextId()].join('-')

      if (policy.alarms().isEmpty()) {
        return fallback
      }
      CloudWatchClient sourceCloudWatch = amazonClientProvider.getAmazonCloudWatchV2(sourceCredentials, sourceRegion)
      List<MetricAlarm> sourceAlarms = new AlarmRetriever(sourceCloudWatch).retrieve(DescribeAlarmsRequest.builder().alarmNames([policy.alarms()[0].alarmName()]).build())
      if (sourceAlarms.isEmpty()) {
        return fallback
      }
      MetricAlarm alarm = sourceAlarms[0]
      // 'PolicyName' cannot contain a ':' character but it is a valid character in Cloudwatch Namespace and Metric names.
      return [
        targetAsgName,
        alarm.namespace(),
        alarm.metricName(),
        alarm.comparisonOperatorAsString(),
        alarm.threshold(),
        alarm.evaluationPeriods(),
        alarm.period(),
        new Date().getTime()
      ].join('-').replace(':', '-')
    }
  }
}
