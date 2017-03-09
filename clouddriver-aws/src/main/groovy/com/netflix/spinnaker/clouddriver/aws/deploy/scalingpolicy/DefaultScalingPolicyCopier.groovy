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
import org.springframework.beans.factory.annotation.Autowired

class DefaultScalingPolicyCopier implements ScalingPolicyCopier {

  public static final DIMENSION_NAME_FOR_ASG = 'AutoScalingGroupName'

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  IdGenerator idGenerator

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
    Map<String, String> sourcePolicyArnToTargetPolicyArn = [:]
    sourceAsgScalingPolicies.each { sourceAsgScalingPolicy ->
      String newPolicyName = [targetAsgName, 'policy', idGenerator.nextId()].join('-')
      def policyRequest = buildNewPolicyRequest(newPolicyName, sourceAsgScalingPolicy, targetAsgName)
      def result = targetAutoScaling.putScalingPolicy(policyRequest)
      sourcePolicyArnToTargetPolicyArn[sourceAsgScalingPolicy.policyARN] = result.policyARN

      task.updateStatus "AWS_DEPLOY", "Creating scaling policy (${policyRequest}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."
    }
    Collection<String> allSourceAlarmNames = sourceAsgScalingPolicies*.alarms*.alarmName.flatten().unique()
    if (allSourceAlarmNames) {
      copyAlarmsForAsg(targetAsgName, allSourceAlarmNames, sourcePolicyArnToTargetPolicyArn, sourceCredentials, targetCredentials, sourceRegion, targetRegion)
    }
  }

  protected PutScalingPolicyRequest buildNewPolicyRequest(String newPolicyName, ScalingPolicy sourceAsgScalingPolicy, String targetAsgName) {
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
    )
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
    def replacePolicyArnActions = { Collection<String> actions ->
      sourcePolicyArnToTargetPolicyArn.each { sourcePolicyArn, targetPolicyArn ->
        if (sourcePolicyArn in actions) {
          actions = (actions - sourcePolicyArn) + targetPolicyArn
        }
      }
      actions
    }
    sourceAlarms.each { alarm ->
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
        oKActions: replacePolicyArnActions(alarm.oKActions),
        alarmActions: replacePolicyArnActions(alarm.alarmActions),
        insufficientDataActions: replacePolicyArnActions(alarm.insufficientDataActions),
        metricName: alarm.metricName,
        namespace: alarm.namespace,
        statistic: alarm.statistic,
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
}
