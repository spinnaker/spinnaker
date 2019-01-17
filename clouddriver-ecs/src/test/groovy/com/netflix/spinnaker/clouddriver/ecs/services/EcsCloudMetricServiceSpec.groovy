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

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling
import com.amazonaws.services.applicationautoscaling.model.*
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsCloudMetricServiceSpec extends Specification {
  def metricAlarmCacheClient = Mock(EcsCloudWatchAlarmCacheClient)

  def sourceAutoScaling = Mock(AWSApplicationAutoScaling)
  def targetAutoScaling = Mock(AWSApplicationAutoScaling)
  def sourceCloudWatch = Mock(AmazonCloudWatch)
  def targetCloudWatch = Mock(AmazonCloudWatch)
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
  def sourceCredentials = Stub(NetflixAmazonCredentials) {
    getAccountId() >> sourceAccountId
  }
  def targetCredentials = Stub(NetflixAmazonCredentials) {
    getAccountId() >> targetAccountId
  }
  def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials(sourceAccountName) >> sourceCredentials
    getCredentials(targetAccountName) >> targetCredentials
  }
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonApplicationAutoScaling(sourceCredentials, sourceRegion, false) >> sourceAutoScaling
    getAmazonApplicationAutoScaling(targetCredentials, targetRegion, false) >> targetAutoScaling
    getAmazonCloudWatch(sourceCredentials, sourceRegion, false) >> sourceCloudWatch
    getAmazonCloudWatch(targetCredentials, targetRegion, false) >> targetCloudWatch
  }

  @Subject
  def service = new EcsCloudMetricService()

  def setup() {
    service.amazonClientProvider = amazonClientProvider
    service.accountCredentialsProvider = accountCredentialsProvider
    service.metricAlarmCacheClient = metricAlarmCacheClient
  }

  void 'should copy nothing when there are no scaling policies'() {
    when:
    service.copyScalingPolicies(targetAccountName, targetRegion, targetServiceName, targetResourceId,
      sourceAccountName, sourceRegion, sourceServiceName, sourceResourceId, clusterName)

    then:
    1 * sourceAutoScaling.describeScalingPolicies(new DescribeScalingPoliciesRequest(
      serviceNamespace: "ecs",
      resourceId: sourceResourceId)) >> new DescribeScalingPoliciesResult(scalingPolicies: [])
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
    1 * sourceAutoScaling.describeScalingPolicies(new DescribeScalingPoliciesRequest(
      serviceNamespace: ServiceNamespace.Ecs,
      resourceId: sourceResourceId)) >>
      new DescribeScalingPoliciesResult(scalingPolicies: [
        new ScalingPolicy(
          policyName: 'policy1',
          policyARN: 'oldPolicyARN1',
          resourceId: 'service/default/asgard-v000',
          policyType: 'TargetTrackingScaling',
          serviceNamespace: 'ecs',
          scalableDimension: 'ecs:service:DesiredCount',
          targetTrackingScalingPolicyConfiguration: new TargetTrackingScalingPolicyConfiguration(
            targetValue: 30.0,
            predefinedMetricSpecification: new PredefinedMetricSpecification(
              predefinedMetricType: 'ECSServiceAverageCPUUtilization'
            ),
            scaleOutCooldown: 300,
            scaleInCooldown: 300
          ),
          alarms: ['TargetTracking-alarm1', 'TargetTracking-alarm2'].collect { new Alarm(alarmName: it) }
        ),
        new ScalingPolicy(
          policyName: 'policy2',
          policyARN: 'oldPolicyARN2',
          resourceId: 'service/default/asgard-v000',
          policyType: 'TargetTrackingScaling',
          serviceNamespace: 'ecs',
          scalableDimension: 'ecs:service:DesiredCount',
          targetTrackingScalingPolicyConfiguration: new TargetTrackingScalingPolicyConfiguration(
            targetValue: 20.0,
            customizedMetricSpecification: new CustomizedMetricSpecification(
              metricName: 'CPUUtilization',
              dimensions: [
                new MetricDimension(name: 'ClusterName', value: 'default'),
                new MetricDimension(name: 'ServiceName', value: 'asgard-v000')
              ],
              namespace: 'AWS/ECS',
              statistic: 'Average',
              unit: 'Percent'
            ),
            scaleOutCooldown: 200,
            scaleInCooldown: 200
          ),
          alarms: ['TargetTracking-alarm3', 'TargetTracking-alarm4'].collect { new Alarm(alarmName: it) }
        ),
        new ScalingPolicy(
          policyName: 'policy3-asgard-v000',
          policyARN: 'oldPolicyARN3',
          resourceId: 'service/default/asgard-v000',
          policyType: 'StepScaling',
          serviceNamespace: 'ecs',
          scalableDimension: 'ecs:service:DesiredCount',
          stepScalingPolicyConfiguration: new StepScalingPolicyConfiguration(
            adjustmentType: 'ChangeInCapacity',
            minAdjustmentMagnitude: 20,
            metricAggregationType: 'Average',
            cooldown: 100,
            stepAdjustments: [
              new StepAdjustment(
               metricIntervalLowerBound: 10.5,
               metricIntervalUpperBound: 11.5,
               scalingAdjustment: 90,
              )
            ],
          ),
          alarms: ['alarm5', 'alarm6-asgard-v000'].collect { new Alarm(alarmName: it) }
        )
      ]
      )

    1 * targetAutoScaling.putScalingPolicy(new PutScalingPolicyRequest(
      policyName: 'policy1-asgard-v001',
      resourceId: 'service/default/asgard-v001',
      policyType: 'TargetTrackingScaling',
      serviceNamespace: 'ecs',
      scalableDimension: 'ecs:service:DesiredCount',
      targetTrackingScalingPolicyConfiguration: new TargetTrackingScalingPolicyConfiguration(
        targetValue: 30.0,
        predefinedMetricSpecification: new PredefinedMetricSpecification(
          predefinedMetricType: 'ECSServiceAverageCPUUtilization'
        ),
        scaleOutCooldown: 300,
        scaleInCooldown: 300
      )
    )) >> new PutScalingPolicyResult(policyARN: 'newPolicyARN1')

    1 * targetAutoScaling.putScalingPolicy(new PutScalingPolicyRequest(
      policyName: 'policy2-asgard-v001',
      resourceId: 'service/default/asgard-v001',
      policyType: 'TargetTrackingScaling',
      serviceNamespace: 'ecs',
      scalableDimension: 'ecs:service:DesiredCount',
      targetTrackingScalingPolicyConfiguration: new TargetTrackingScalingPolicyConfiguration(
        targetValue: 20.0,
        customizedMetricSpecification: new CustomizedMetricSpecification(
          metricName: 'CPUUtilization',
          dimensions: [
            new MetricDimension(name: 'ClusterName', value: 'default'),
            new MetricDimension(name: 'ServiceName', value: 'asgard-v001')
          ],
          namespace: 'AWS/ECS',
          statistic: 'Average',
          unit: 'Percent'
        ),
        scaleOutCooldown: 200,
        scaleInCooldown: 200
      )
    )) >> new PutScalingPolicyResult(policyARN: 'newPolicyARN2')

    1 * targetAutoScaling.putScalingPolicy(new PutScalingPolicyRequest(
      policyName: 'policy3-asgard-v001',
      resourceId: 'service/default/asgard-v001',
      policyType: 'StepScaling',
      serviceNamespace: 'ecs',
      scalableDimension: 'ecs:service:DesiredCount',
      stepScalingPolicyConfiguration: new StepScalingPolicyConfiguration(
        adjustmentType: 'ChangeInCapacity',
        minAdjustmentMagnitude: 20,
        metricAggregationType: 'Average',
        cooldown: 100,
        stepAdjustments: [
          new StepAdjustment(
            metricIntervalLowerBound: 10.5,
            metricIntervalUpperBound: 11.5,
            scalingAdjustment: 90,
          )
        ],
      )
    )) >> new PutScalingPolicyResult(policyARN: 'newPolicyARN3')

    1 * sourceCloudWatch.describeAlarms(_) >> new DescribeAlarmsResult(metricAlarms: [
      new MetricAlarm(
        alarmName: 'TargetTracking-alarm1',
        alarmDescription: 'alarm 1 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN1'],
        insufficientDataActions: [],
        metricName: 'metric1',
        namespace: 'AWS/ECS',
        statistic: 'statistic1',
        dimensions: [
          new Dimension(name: 'ClusterName', value: 'default'),
          new Dimension(name: 'ServiceName', value: 'asgard-v000')
        ],
        period: 1,
        unit: 'unit1',
        evaluationPeriods: 2,
        threshold: 4.2,
        comparisonOperator: 'GreaterThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'TargetTracking-alarm2',
        alarmDescription: 'alarm 2 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN1'],
        insufficientDataActions: [],
        metricName: 'metric2',
        namespace: 'hello',
        statistic: 'statistic2',
        dimensions: [
          new Dimension(name: 'ClusterName', value: 'default'),
          new Dimension(name: 'ServiceName', value: 'asgard-v000'),
          new Dimension(name: 'other', value: 'dimension1')
        ],
        period: 10,
        unit: 'unit2',
        evaluationPeriods: 20,
        threshold: 40.2,
        comparisonOperator: 'LessThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'TargetTracking-alarm3',
        alarmDescription: 'alarm 3 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN2'],
        insufficientDataActions: [],
        metricName: 'metric3',
        namespace: 'AWS/ECS',
        statistic: 'statistic3',
        dimensions: [
          new Dimension(name: 'ClusterName', value: 'default'),
          new Dimension(name: 'ServiceName', value: 'asgard-v000')
        ],
        period: 1,
        unit: 'unit3',
        evaluationPeriods: 2,
        threshold: 4.2,
        comparisonOperator: 'GreaterThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'TargetTracking-alarm4',
        alarmDescription: 'alarm 4 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN2'],
        insufficientDataActions: [],
        metricName: 'metric4',
        namespace: 'hello',
        statistic: 'statistic4',
        dimensions: [
          new Dimension(name: 'ClusterName', value: 'default'),
          new Dimension(name: 'ServiceName', value: 'asgard-v000')
        ],
        period: 10,
        unit: 'unit4',
        evaluationPeriods: 20,
        threshold: 40.2,
        comparisonOperator: 'LessThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'alarm5',
        alarmDescription: 'alarm 5 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN3'],
        insufficientDataActions: [],
        metricName: 'metric5',
        namespace: 'other',
        statistic: 'statistic5',
        dimensions: [
          new Dimension(name: 'hello', value: 'world')
        ],
        period: 1,
        unit: 'unit5',
        evaluationPeriods: 2,
        threshold: 4.2,
        comparisonOperator: 'GreaterThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'alarm6-asgard-v000',
        alarmDescription: 'alarm 6 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN3'],
        insufficientDataActions: [],
        metricName: 'metric6',
        namespace: 'AWS/ECS',
        statistic: 'statistic6',
        dimensions: [
          new Dimension(name: 'ClusterName', value: 'default'),
          new Dimension(name: 'ServiceName', value: 'asgard-v000')
        ],
        period: 10,
        unit: 'unit6',
        evaluationPeriods: 20,
        threshold: 40.2,
        comparisonOperator: 'LessThanOrEqualToThreshold'
      ),
    ])
    1 * targetCloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: 'alarm5-asgard-v001',
      alarmDescription: 'alarm 5 description',
      actionsEnabled: true,
      oKActions: [],
      alarmActions: ['newPolicyARN3'],
      insufficientDataActions: [],
      metricName: 'metric5',
      namespace: 'other',
      statistic: 'statistic5',
      dimensions: [
        new Dimension(name: 'hello', value: 'world')
      ],
      period: 1,
      unit: 'unit5',
      evaluationPeriods: 2,
      threshold: 4.2,
      comparisonOperator: 'GreaterThanOrEqualToThreshold'
    ))
    1 * targetCloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: 'alarm6-asgard-v001',
      alarmDescription: 'alarm 6 description',
      actionsEnabled: true,
      oKActions: [],
      alarmActions: ['newPolicyARN3'],
      insufficientDataActions: [],
      metricName: 'metric6',
      namespace: 'AWS/ECS',
      statistic: 'statistic6',
      dimensions: [
        new Dimension(name: 'ClusterName', value: 'default'),
        new Dimension(name: 'ServiceName', value: 'asgard-v001')
      ],
      period: 10,
      unit: 'unit6',
      evaluationPeriods: 20,
      threshold: 40.2,
      comparisonOperator: 'LessThanOrEqualToThreshold'
    ))
  }

  def 'should delete metric alarms'() {
    given:
    def metricAlarms = []
    5.times {
      metricAlarms << new EcsMetricAlarm(
        accountName: targetAccountName,
        region: targetRegion
      )
    }

    metricAlarmCacheClient.getMetricAlarms(_, _, _) >> metricAlarms

    when:
    service.deleteMetrics(targetServiceName, targetAccountName, targetRegion)

    then:
    1 * targetCloudWatch.deleteAlarms(_)
  }
}
