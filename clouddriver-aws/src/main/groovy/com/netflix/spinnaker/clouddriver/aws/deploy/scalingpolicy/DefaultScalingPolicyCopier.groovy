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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
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
    AmazonAutoScaling sourceAutoScaling = amazonClientProvider.getAutoScaling(sourceCredentials, sourceRegion, true)
    AmazonAutoScaling targetAutoScaling = amazonClientProvider.getAutoScaling(targetCredentials, targetRegion, true)
    List<ScalingPolicy> sourceAsgScalingPolicies = new ScalingPolicyRetriever(sourceAutoScaling).retrieve(new DescribePoliciesRequest(autoScalingGroupName: sourceAsgName))

    log.info("Copying scaling policies for $sourceAsgName to $targetAsgName: $sourceAsgScalingPolicies")

    Map<String, String> sourcePolicyArnToTargetPolicyArn = [:]
    sourceAsgScalingPolicies.each { sourceAsgScalingPolicy ->
      String newPolicyName = policyNameGenerator.generateScalingPolicyName(sourceCredentials, sourceRegion, sourceAsgName, targetAsgName, sourceAsgScalingPolicy)
      def policyRequest = buildNewPolicyRequest(newPolicyName, sourceAsgScalingPolicy, targetAsgName)
      task.updateStatus "AWS_DEPLOY", "Creating scaling policy (${policyRequest}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."

      def result = targetAutoScaling.putScalingPolicy(policyRequest)
      sourcePolicyArnToTargetPolicyArn[sourceAsgScalingPolicy.policyARN] = result.policyARN

      task.updateStatus "AWS_DEPLOY", "Created scaling policy (${policyRequest}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."
    }
    Collection<String> allSourceAlarmNames = sourceAsgScalingPolicies*.alarms*.alarmName.flatten().unique()
    if (allSourceAlarmNames) {
      copyAlarmsForAsg(targetAsgName, allSourceAlarmNames, sourcePolicyArnToTargetPolicyArn, sourceCredentials, targetCredentials, sourceRegion, targetRegion)
    }
  }

  protected PutScalingPolicyRequest buildNewPolicyRequest(String newPolicyName, ScalingPolicy sourceAsgScalingPolicy, String targetAsgName) {
    if (sourceAsgScalingPolicy.targetTrackingConfiguration) {
      if (sourceAsgScalingPolicy.targetTrackingConfiguration.customizedMetricSpecification) {
        // update target tracking policies to point to the new ASG
        // this will cause grief if a target tracking policy is configured against a *different* ASG, but we are doing
        // the same thing with simple and step policies and have not had any issues thus far
        sourceAsgScalingPolicy.targetTrackingConfiguration.customizedMetricSpecification.dimensions
          .findAll { d ->
          d.name == DIMENSION_NAME_FOR_ASG
        }
        .each { d ->
          d.value = targetAsgName
        }
      }
    }
    return new PutScalingPolicyRequest(
      autoScalingGroupName: targetAsgName,
      policyName: newPolicyName,
      policyType: sourceAsgScalingPolicy.policyType,
      scalingAdjustment: sourceAsgScalingPolicy.scalingAdjustment,
      adjustmentType: sourceAsgScalingPolicy.adjustmentType,
      cooldown: sourceAsgScalingPolicy.cooldown,
      minAdjustmentStep: sourceAsgScalingPolicy.minAdjustmentStep,
      minAdjustmentMagnitude: sourceAsgScalingPolicy.minAdjustmentMagnitude,
      metricAggregationType: sourceAsgScalingPolicy.metricAggregationType,
      stepAdjustments: sourceAsgScalingPolicy.stepAdjustments,
      estimatedInstanceWarmup: sourceAsgScalingPolicy.estimatedInstanceWarmup,
      targetTrackingConfiguration: sourceAsgScalingPolicy.targetTrackingConfiguration
    )
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
    AmazonCloudWatch sourceCloudWatch = amazonClientProvider.getCloudWatch(sourceCredentials, sourceRegion, true)
    AmazonCloudWatch targetCloudWatch = amazonClientProvider.getCloudWatch(targetCredentials, targetRegion, true)
    List<MetricAlarm> sourceAlarms = new AlarmRetriever(sourceCloudWatch).retrieve(new DescribeAlarmsRequest(alarmNames: sourceAlarmNames))

    log.info("Copying scaling policy alarms for $newAutoScalingGroupName: $sourceAlarms")
    
    sourceAlarms.findAll{ shouldCopySourceAlarm(it) }.each { alarm ->
      List<Dimension> newDimensions = Lists.newArrayList(alarm.dimensions)
      Dimension asgDimension = newDimensions.find { it.name == DIMENSION_NAME_FOR_ASG }
      if (asgDimension) {
        newDimensions.remove(asgDimension)
        newDimensions.add(new Dimension(name: DIMENSION_NAME_FOR_ASG, value: newAutoScalingGroupName))
      }
      String newAlarmName = [newAutoScalingGroupName, 'alarm', idGenerator.nextId()].join('-')
      def request = new PutMetricAlarmRequest(
        alarmName: newAlarmName,
        alarmDescription: alarm.alarmDescription,
        actionsEnabled: alarm.actionsEnabled,
        oKActions: replacePolicyArnActions(sourceRegion, targetRegion, sourceCredentials, targetCredentials, sourcePolicyArnToTargetPolicyArn, alarm.oKActions),
        alarmActions: replacePolicyArnActions(sourceRegion, targetRegion, sourceCredentials, targetCredentials, sourcePolicyArnToTargetPolicyArn, alarm.alarmActions),
        insufficientDataActions: replacePolicyArnActions(sourceRegion, targetRegion, sourceCredentials, targetCredentials, sourcePolicyArnToTargetPolicyArn, alarm.insufficientDataActions),
        metricName: alarm.metricName,
        namespace: alarm.namespace,
        statistic: alarm.statistic,
        extendedStatistic: alarm.extendedStatistic,
        dimensions: newDimensions,
        period: alarm.period,
        unit: alarm.unit,
        evaluationPeriods: alarm.evaluationPeriods,
        threshold: alarm.threshold,
        comparisonOperator: alarm.comparisonOperator
      )
      targetCloudWatch.putMetricAlarm(request)
    }
  }

  protected boolean shouldCopySourceAlarm(MetricAlarm metricAlarm) {
    // AWS auto-creates TargetTracking alarms, so we don't want to copy them (otherwise, we'll have duplicates)
    return !metricAlarm.alarmName.startsWith("TargetTracking-")
  }

  @Canonical
  static class ScalingPolicyRetriever extends AwsResultsRetriever<ScalingPolicy, DescribePoliciesRequest, DescribePoliciesResult> {
    final AmazonAutoScaling autoScaling

    @Override
    protected DescribePoliciesResult makeRequest(DescribePoliciesRequest request) {
      autoScaling.describePolicies(request)
    }

    @Override
    protected List<ScalingPolicy> accessResult(DescribePoliciesResult result) {
      result.scalingPolicies
    }
  }

  @Canonical
  static class AlarmRetriever extends AwsResultsRetriever<MetricAlarm, DescribeAlarmsRequest, DescribeAlarmsResult> {
    final AmazonCloudWatch cloudWatch

    @Override
    protected DescribeAlarmsResult makeRequest(DescribeAlarmsRequest request) {
      cloudWatch.describeAlarms(request)
    }

    @Override
    protected List<MetricAlarm> accessResult(DescribeAlarmsResult result) {
      result.metricAlarms
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
      policy.policyName.replaceAll(sourceAsgName, targetAsgName)
      String fallback = policy.policyName.contains(sourceAsgName) ?
        policy.policyName.replaceAll(sourceAsgName, targetAsgName) :
        [policy.policyName, 'no-alarm', idGenerator.nextId()].join('-')

      if (policy.alarms.isEmpty()) {
        return fallback
      }
      AmazonCloudWatch sourceCloudWatch = amazonClientProvider.getCloudWatch(sourceCredentials, sourceRegion, true)
      List<MetricAlarm> sourceAlarms = new AlarmRetriever(sourceCloudWatch).retrieve(new DescribeAlarmsRequest(alarmNames: [policy.alarms[0].alarmName]))
      if (sourceAlarms.isEmpty()) {
        return fallback
      }
      MetricAlarm alarm = sourceAlarms[0]
      return [targetAsgName, alarm.namespace, alarm.metricName, alarm.comparisonOperator, alarm.threshold, alarm.evaluationPeriods, alarm.period, new Date().getTime()].join('-')
    }
  }
}
