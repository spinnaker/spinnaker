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
package com.netflix.spinnaker.kato.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.*
import com.google.common.collect.Lists
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.kato.aws.services.IdGenerator
import com.netflix.spinnaker.kato.data.task.Task
import groovy.transform.Canonical

@Canonical
class AsgReferenceCopier {

  static final DIMENSION_NAME_FOR_ASG = 'AutoScalingGroupName'

  final AmazonClientProvider amazonClientProvider

  final NetflixAmazonCredentials sourceCredentials
  final String sourceRegion

  final NetflixAmazonCredentials targetCredentials
  final String targetRegion

  IdGenerator idGenerator

  void copyScheduledActionsForAsg(Task task, String sourceAsgName, String targetAsgName) {
    AmazonAutoScaling sourceAutoScaling = amazonClientProvider.getAutoScaling(sourceCredentials, sourceRegion)
    AmazonAutoScaling targetAutoScaling = amazonClientProvider.getAutoScaling(targetCredentials, targetRegion)
    def sourceScheduledActions = new ScheduledActionsRetriever(sourceAutoScaling).retrieve(new DescribeScheduledActionsRequest(autoScalingGroupName: sourceAsgName))
    sourceScheduledActions.each { sourceScheduledAction ->
      String newScheduledActionName = [targetAsgName, 'schedule', idGenerator.nextId()].join('-')
      def request = new PutScheduledUpdateGroupActionRequest(
        autoScalingGroupName: targetAsgName,
        scheduledActionName: newScheduledActionName,
        endTime: sourceScheduledAction.endTime,
        recurrence: sourceScheduledAction.recurrence,
        minSize: sourceScheduledAction.minSize,
        maxSize: sourceScheduledAction.maxSize,
        desiredCapacity: sourceScheduledAction.desiredCapacity
      )
      Date startTime = sourceScheduledAction.startTime ?: sourceScheduledAction.time
      if (startTime?.time > System.currentTimeMillis()) {
        request.withStartTime(startTime)
      }
      targetAutoScaling.putScheduledUpdateGroupAction(request)

      task.updateStatus "AWS_DEPLOY", "Creating scheduled action (${request}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."
    }
  }

  void copyScalingPoliciesWithAlarms(Task task, String sourceAsgName, String targetAsgName) {
    AmazonAutoScaling sourceAutoScaling = amazonClientProvider.getAutoScaling(sourceCredentials, sourceRegion)
    AmazonAutoScaling targetAutoScaling = amazonClientProvider.getAutoScaling(targetCredentials, targetRegion)
    List<ScalingPolicy> sourceAsgScalingPolicies = new ScalingPolicyRetriever(sourceAutoScaling).retrieve(new DescribePoliciesRequest(autoScalingGroupName: sourceAsgName))
    Map<String, String> sourcePolicyArnToTargetPolicyArn = [:]
    sourceAsgScalingPolicies.each { sourceAsgScalingPolicy ->
      String newPolicyName = [targetAsgName, 'policy', idGenerator.nextId()].join('-')
      def policyRequest = new PutScalingPolicyRequest(
        autoScalingGroupName: targetAsgName,
        policyName: newPolicyName,
        scalingAdjustment: sourceAsgScalingPolicy.scalingAdjustment,
        adjustmentType: sourceAsgScalingPolicy.adjustmentType,
        cooldown: sourceAsgScalingPolicy.cooldown,
        minAdjustmentStep: sourceAsgScalingPolicy.minAdjustmentStep
      )
      def result = targetAutoScaling.putScalingPolicy(policyRequest)
      sourcePolicyArnToTargetPolicyArn[sourceAsgScalingPolicy.policyARN] = result.policyARN

      task.updateStatus "AWS_DEPLOY", "Creating scaling policy (${policyRequest}) on ${targetRegion}/${targetAsgName} from ${sourceRegion}/${sourceAsgName}..."
    }
    Collection<String> allSourceAlarmNames = sourceAsgScalingPolicies*.alarms*.alarmName.flatten().unique()
    if (allSourceAlarmNames) {
      copyAlarmsForAsg(targetAsgName, allSourceAlarmNames, sourcePolicyArnToTargetPolicyArn)
    }
  }

  void copyAlarmsForAsg(String newAutoScalingGroupName, Collection<String> sourceAlarmNames, Map<String, String> sourcePolicyArnToTargetPolicyArn) {
    AmazonCloudWatch sourceCloudWatch = amazonClientProvider.getCloudWatch(sourceCredentials, sourceRegion)
    AmazonCloudWatch targetCloudWatch = amazonClientProvider.getCloudWatch(targetCredentials, targetRegion)
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

  @Canonical
  static class ScheduledActionsRetriever extends AwsResultsRetriever<ScheduledUpdateGroupAction, DescribeScheduledActionsRequest, DescribeScheduledActionsResult> {
    final AmazonAutoScaling autoScaling

    @Override
    protected DescribeScheduledActionsResult makeRequest(DescribeScheduledActionsRequest request) {
      autoScaling.describeScheduledActions(request)
    }

    @Override
    protected List<ScheduledUpdateGroupAction> accessResult(DescribeScheduledActionsResult result) {
      result.scheduledUpdateGroupActions
    }
  }

}
