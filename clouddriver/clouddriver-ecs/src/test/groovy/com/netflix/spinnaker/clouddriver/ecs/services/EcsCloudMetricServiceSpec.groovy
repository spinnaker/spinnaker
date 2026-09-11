/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.services

import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient
import software.amazon.awssdk.services.applicationautoscaling.model.Alarm
import software.amazon.awssdk.services.applicationautoscaling.model.CustomizedMetricSpecification
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse
import software.amazon.awssdk.services.applicationautoscaling.model.MetricDimension
import software.amazon.awssdk.services.applicationautoscaling.model.PredefinedMetricSpecification
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyResponse
import software.amazon.awssdk.services.applicationautoscaling.model.ScalingPolicy
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace
import software.amazon.awssdk.services.applicationautoscaling.model.StepAdjustment
import software.amazon.awssdk.services.applicationautoscaling.model.StepScalingPolicyConfiguration
import software.amazon.awssdk.services.applicationautoscaling.model.TargetTrackingScalingPolicyConfiguration
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.DeleteAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricServiceSpec extends Specification {
  def metricAlarmCacheClient = Mock(EcsCloudWatchAlarmCacheClient)

  def sourceAutoScaling = Mock(ApplicationAutoScalingClient)
  def targetAutoScaling = Mock(ApplicationAutoScalingClient)
  def sourceCloudWatch = Mock(CloudWatchClient)
  def targetCloudWatch = Mock(CloudWatchClient)
  def sourceAccountName = 'abc123'
  def targetAccountName = 'def456'
  def sourceAccountId = 'abc'
  def targetAccountId = 'def'
  def sourceRegion = 'us-east-1'
  def targetRegion = 'us-west-1'
  def clusterName = 'default'
  def sourceServiceName = 'asgard-v000'
  def targetServiceName = 'asgard-v001'
  def sourceResourceId = 'service/default/asgard-v000'
  def targetResourceId = 'service/default/asgard-v001'
  def sourceCredentials = Stub(NetflixECSCredentials) {
    getAccountId() >> sourceAccountId
  }
  def targetCredentials = Stub(NetflixECSCredentials) {
    getAccountId() >> targetAccountId
  }
  def credentialsRepository = Stub(CredentialsRepository) {
    getOne(sourceAccountName) >> sourceCredentials
    getOne(targetAccountName) >> targetCredentials
  }
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonApplicationAutoScalingV2(sourceCredentials, sourceRegion) >> sourceAutoScaling
    getAmazonApplicationAutoScalingV2(targetCredentials, targetRegion) >> targetAutoScaling
    getAmazonCloudWatchV2(sourceCredentials, sourceRegion) >> sourceCloudWatch
    getAmazonCloudWatchV2(targetCredentials, targetRegion) >> targetCloudWatch
  }

  @Subject
  def service = new EcsCloudMetricService()

  def setup() {
    service.amazonClientProvider = amazonClientProvider
    service.credentialsRepository = credentialsRepository
    service.metricAlarmCacheClient = metricAlarmCacheClient
  }

  void 'should copy nothing when there are no scaling policies'() {
    when:
    service.copyScalingPolicies(targetAccountName, targetRegion, targetServiceName, targetResourceId,
      sourceAccountName, sourceRegion, sourceServiceName, sourceResourceId, clusterName)

    then:
    1 * sourceAutoScaling.describeScalingPolicies(DescribeScalingPoliciesRequest.builder()
      .serviceNamespace("ecs")
      .resourceId(sourceResourceId).build()) >> DescribeScalingPoliciesResponse.builder().scalingPolicies([]).build()
    0 * targetAutoScaling.putScalingPolicy(_)
    0 * sourceCloudWatch.describeAlarms(_)
    0 * targetCloudWatch.putMetricAlarm(_)
  }

  void 'should replace scaling policy ARNs and omit actions that are specific to the source account/region when they differ'() {
    given:
    def replacements = ['oldPolicyARN': 'newPolicyARN']
    def actions = ['oldPolicyARN', 'sns:us-east-1', "sns:${sourceCredentials.accountId}:someQueue".toString(), 'ok-one']

    when:
    def replacedActions = service.replacePolicyArnActions(
      sourceRegion, targetRegion,
      sourceAccountId, targetAccountId,
      replacements, actions)

    then:
    replacedActions.sort() == ['newPolicyARN', 'ok-one']
  }

  void 'should copy scaling policies and alarms'() {
    when:
    service.copyScalingPolicies(targetAccountName, targetRegion, targetServiceName, targetResourceId,
      sourceAccountName, sourceRegion, sourceServiceName, sourceResourceId, clusterName)

    then:
    1 * sourceAutoScaling.describeScalingPolicies(DescribeScalingPoliciesRequest.builder()
      .serviceNamespace(ServiceNamespace.ECS)
      .resourceId(sourceResourceId).build()) >>
      DescribeScalingPoliciesResponse.builder().scalingPolicies([
        ScalingPolicy.builder()
          .policyName('policy1')
          .policyARN('oldPolicyARN1')
          .resourceId('service/default/asgard-v000')
          .policyType('TargetTrackingScaling')
          .serviceNamespace('ecs')
          .scalableDimension('ecs:service:DesiredCount')
          .targetTrackingScalingPolicyConfiguration(TargetTrackingScalingPolicyConfiguration.builder()
            .targetValue(30.0)
            .predefinedMetricSpecification(PredefinedMetricSpecification.builder()
              .predefinedMetricType('ECSServiceAverageCPUUtilization').build())
            .scaleOutCooldown(300)
            .scaleInCooldown(300).build())
          .alarms(['TargetTracking-alarm1', 'TargetTracking-alarm2'].collect { Alarm.builder().alarmName(it).build() })
          .build(),
        ScalingPolicy.builder()
          .policyName('policy2')
          .policyARN('oldPolicyARN2')
          .resourceId('service/default/asgard-v000')
          .policyType('TargetTrackingScaling')
          .serviceNamespace('ecs')
          .scalableDimension('ecs:service:DesiredCount')
          .targetTrackingScalingPolicyConfiguration(TargetTrackingScalingPolicyConfiguration.builder()
            .targetValue(20.0)
            .customizedMetricSpecification(CustomizedMetricSpecification.builder()
              .metricName('CPUUtilization')
              .dimensions([
                MetricDimension.builder().name('ClusterName').value('default').build(),
                MetricDimension.builder().name('ServiceName').value('asgard-v000').build()
              ])
              .namespace('AWS/ECS')
              .statistic('Average')
              .unit('Percent').build())
            .scaleOutCooldown(200)
            .scaleInCooldown(200).build())
          .alarms(['TargetTracking-alarm3', 'TargetTracking-alarm4'].collect { Alarm.builder().alarmName(it).build() })
          .build(),
        ScalingPolicy.builder()
          .policyName('policy3-asgard-v000')
          .policyARN('oldPolicyARN3')
          .resourceId('service/default/asgard-v000')
          .policyType('StepScaling')
          .serviceNamespace('ecs')
          .scalableDimension('ecs:service:DesiredCount')
          .stepScalingPolicyConfiguration(StepScalingPolicyConfiguration.builder()
            .adjustmentType('ChangeInCapacity')
            .minAdjustmentMagnitude(20)
            .metricAggregationType('Average')
            .cooldown(100)
            .stepAdjustments([
              StepAdjustment.builder()
                .metricIntervalLowerBound(10.5)
                .metricIntervalUpperBound(11.5)
                .scalingAdjustment(90)
                .build()
            ]).build())
          .alarms(['alarm5', 'alarm6-asgard-v000'].collect { Alarm.builder().alarmName(it).build() })
          .build()
      ]).build()

    1 * targetAutoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName('policy1-asgard-v001')
      .resourceId('service/default/asgard-v001')
      .policyType('TargetTrackingScaling')
      .serviceNamespace('ecs')
      .scalableDimension('ecs:service:DesiredCount')
      .targetTrackingScalingPolicyConfiguration(TargetTrackingScalingPolicyConfiguration.builder()
        .targetValue(30.0)
        .predefinedMetricSpecification(PredefinedMetricSpecification.builder()
          .predefinedMetricType('ECSServiceAverageCPUUtilization').build())
        .scaleOutCooldown(300)
        .scaleInCooldown(300).build())
      .build()) >> PutScalingPolicyResponse.builder().policyARN('newPolicyARN1').build()

    1 * targetAutoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName('policy2-asgard-v001')
      .resourceId('service/default/asgard-v001')
      .policyType('TargetTrackingScaling')
      .serviceNamespace('ecs')
      .scalableDimension('ecs:service:DesiredCount')
      .targetTrackingScalingPolicyConfiguration(TargetTrackingScalingPolicyConfiguration.builder()
        .targetValue(20.0)
        .customizedMetricSpecification(CustomizedMetricSpecification.builder()
          .metricName('CPUUtilization')
          .dimensions([
            MetricDimension.builder().name('ClusterName').value('default').build(),
            MetricDimension.builder().name('ServiceName').value('asgard-v001').build()
          ])
          .namespace('AWS/ECS')
          .statistic('Average')
          .unit('Percent').build())
        .scaleOutCooldown(200)
        .scaleInCooldown(200).build())
      .build()) >> PutScalingPolicyResponse.builder().policyARN('newPolicyARN2').build()

    1 * targetAutoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName('policy3-asgard-v001')
      .resourceId('service/default/asgard-v001')
      .policyType('StepScaling')
      .serviceNamespace('ecs')
      .scalableDimension('ecs:service:DesiredCount')
      .stepScalingPolicyConfiguration(StepScalingPolicyConfiguration.builder()
        .adjustmentType('ChangeInCapacity')
        .minAdjustmentMagnitude(20)
        .metricAggregationType('Average')
        .cooldown(100)
        .stepAdjustments([
          StepAdjustment.builder()
            .metricIntervalLowerBound(10.5)
            .metricIntervalUpperBound(11.5)
            .scalingAdjustment(90)
            .build()
        ]).build())
      .build()) >> PutScalingPolicyResponse.builder().policyARN('newPolicyARN3').build()

    1 * sourceCloudWatch.describeAlarms(_) >> DescribeAlarmsResponse.builder().metricAlarms([
      MetricAlarm.builder()
        .alarmName('TargetTracking-alarm1')
        .alarmDescription('alarm 1 description')
        .actionsEnabled(true)
        .okActions([])
        .alarmActions(['oldPolicyARN1'])
        .insufficientDataActions([])
        .metricName('metric1')
        .namespace('AWS/ECS')
        .statistic('SampleCount')
        .dimensions([
          Dimension.builder().name('ClusterName').value('default').build(),
          Dimension.builder().name('ServiceName').value('asgard-v000').build()
        ])
        .period(1)
        .unit('Seconds')
        .evaluationPeriods(2)
        .threshold(4.2)
        .comparisonOperator('GreaterThanOrEqualToThreshold')
        .build(),
      MetricAlarm.builder()
        .alarmName('TargetTracking-alarm2')
        .alarmDescription('alarm 2 description')
        .actionsEnabled(true)
        .okActions([])
        .alarmActions(['oldPolicyARN1'])
        .insufficientDataActions([])
        .metricName('metric2')
        .namespace('hello')
        .statistic('Average')
        .dimensions([
          Dimension.builder().name('ClusterName').value('default').build(),
          Dimension.builder().name('ServiceName').value('asgard-v000').build(),
          Dimension.builder().name('other').value('dimension1').build()
        ])
        .period(10)
        .unit('Bytes')
        .evaluationPeriods(20)
        .threshold(40.2)
        .comparisonOperator('LessThanOrEqualToThreshold')
        .build(),
      MetricAlarm.builder()
        .alarmName('TargetTracking-alarm3')
        .alarmDescription('alarm 3 description')
        .actionsEnabled(true)
        .okActions([])
        .alarmActions(['oldPolicyARN2'])
        .insufficientDataActions([])
        .metricName('metric3')
        .namespace('AWS/ECS')
        .statistic('Sum')
        .dimensions([
          Dimension.builder().name('ClusterName').value('default').build(),
          Dimension.builder().name('ServiceName').value('asgard-v000').build()
        ])
        .period(1)
        .unit('Bits')
        .evaluationPeriods(2)
        .threshold(4.2)
        .comparisonOperator('GreaterThanOrEqualToThreshold')
        .build(),
      MetricAlarm.builder()
        .alarmName('TargetTracking-alarm4')
        .alarmDescription('alarm 4 description')
        .actionsEnabled(true)
        .okActions([])
        .alarmActions(['oldPolicyARN2'])
        .insufficientDataActions([])
        .metricName('metric4')
        .namespace('hello')
        .statistic('Minimum')
        .dimensions([
          Dimension.builder().name('ClusterName').value('default').build(),
          Dimension.builder().name('ServiceName').value('asgard-v000').build()
        ])
        .period(10)
        .unit('Count')
        .evaluationPeriods(20)
        .threshold(40.2)
        .comparisonOperator('LessThanOrEqualToThreshold')
        .build(),
      MetricAlarm.builder()
        .alarmName('alarm5')
        .alarmDescription('alarm 5 description')
        .actionsEnabled(true)
        .okActions([])
        .alarmActions(['oldPolicyARN3'])
        .insufficientDataActions([])
        .metricName('metric5')
        .namespace('other')
        .statistic('Maximum')
        .dimensions([
          Dimension.builder().name('hello').value('world').build()
        ])
        .period(1)
        .unit('Percent')
        .evaluationPeriods(2)
        .threshold(4.2)
        .comparisonOperator('GreaterThanOrEqualToThreshold')
        .build(),
      MetricAlarm.builder()
        .alarmName('alarm6-asgard-v000')
        .alarmDescription('alarm 6 description')
        .actionsEnabled(true)
        .okActions([])
        .alarmActions(['oldPolicyARN3'])
        .insufficientDataActions([])
        .metricName('metric6')
        .namespace('AWS/ECS')
        .statistic('SampleCount')
        .dimensions([
          Dimension.builder().name('ClusterName').value('default').build(),
          Dimension.builder().name('ServiceName').value('asgard-v000').build()
        ])
        .period(10)
        .unit('None')
        .evaluationPeriods(20)
        .threshold(40.2)
        .comparisonOperator('LessThanOrEqualToThreshold')
        .build()
    ]).build()
    1 * targetCloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName('alarm5-asgard-v001')
      .alarmDescription('alarm 5 description')
      .actionsEnabled(true)
      .okActions([])
      .alarmActions(['newPolicyARN3'])
      .insufficientDataActions([])
      .metricName('metric5')
      .namespace('other')
      .statistic('Maximum')
      .dimensions([
        Dimension.builder().name('hello').value('world').build()
      ])
      .period(1)
      .unit('Percent')
      .evaluationPeriods(2)
      .threshold(4.2)
      .comparisonOperator('GreaterThanOrEqualToThreshold')
      .build())
    1 * targetCloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName('alarm6-asgard-v001')
      .alarmDescription('alarm 6 description')
      .actionsEnabled(true)
      .okActions([])
      .alarmActions(['newPolicyARN3'])
      .insufficientDataActions([])
      .metricName('metric6')
      .namespace('AWS/ECS')
      .statistic('SampleCount')
      .dimensions([
        Dimension.builder().name('ClusterName').value('default').build(),
        Dimension.builder().name('ServiceName').value('asgard-v001').build()
      ])
      .period(10)
      .unit('None')
      .evaluationPeriods(20)
      .threshold(40.2)
      .comparisonOperator('LessThanOrEqualToThreshold')
      .build())
  }

  def 'should delete metric alarms'() {
    given:
    def metricAlarms = []
    5.times {
      metricAlarms << new EcsMetricAlarm(
        accountName: targetAccountName,
        region: targetRegion,
        alarmName: "alarm-name-${it}"
      )
    }

    metricAlarmCacheClient.getMetricAlarms(targetServiceName,targetAccountName,targetRegion,clusterName) >> metricAlarms

    when:
    service.deleteMetrics(targetServiceName, targetAccountName, targetRegion, clusterName)

    then:
    1 * targetCloudWatch.deleteAlarms({ DeleteAlarmsRequest request ->
      request.alarmNames().toSorted() == metricAlarms*.alarmName.sort()
    })
  }


}
