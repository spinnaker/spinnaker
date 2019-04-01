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
import com.amazonaws.services.autoscaling.model.Alarm
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.autoscaling.model.StepAdjustment
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.AsgReferenceCopier
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import spock.lang.Specification
import spock.lang.Subject

class DefaultScalingPolicyCopierSpec extends Specification {

  def sourceAutoScaling = Mock(AmazonAutoScaling)
  def targetAutoScaling = Mock(AmazonAutoScaling)
  def sourceCredentials = Stub(NetflixAmazonCredentials) {
    getAccountId() >> 'abc'
  }
  def targetCredentials = Stub(NetflixAmazonCredentials) {
    getAccountId() >> 'def'
  }
  def sourceCloudWatch = Mock(AmazonCloudWatch)
  def targetCloudWatch = Mock(AmazonCloudWatch)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScaling(_, 'us-east-1', true) >> sourceAutoScaling
    getAutoScaling(_, 'us-west-1', true) >> targetAutoScaling
    getCloudWatch(_, 'us-east-1', true) >> sourceCloudWatch
    getCloudWatch(_, 'us-west-1', true) >> targetCloudWatch
  }
  String newPolicyName = 'new_policy_name'

  def policyNameGenerator = Stub(DefaultScalingPolicyCopier.PolicyNameGenerator) {
    generateScalingPolicyName(_, _, _, _, _) >>
      'new_policy_name'
  }

  int count = 0
  def idGenerator = Stub(IdGenerator) {
    nextId() >> { (++count).toString() }
  }

  void cleanup() {
    count = 0
  }

  @Subject
  ScalingPolicyCopier scalingPolicyCopier = new DefaultScalingPolicyCopier(amazonClientProvider, idGenerator, policyNameGenerator)

  void 'should copy nothing when there are no scaling policies'() {
    when:
    scalingPolicyCopier.copyScalingPolicies(Mock(Task), 'asgard-v000', 'asgard-v001', sourceCredentials, targetCredentials, 'us-east-1', 'us-west-1')

    then:
    1 * sourceAutoScaling.describePolicies(new DescribePoliciesRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribePoliciesResult(scalingPolicies: [])
    0 * targetAutoScaling.putScalingPolicy(_)
    0 * sourceCloudWatch.describeAlarms(_)
    0 * targetCloudWatch.putMetricAlarm(_)
  }

  void 'should omit actions that are specific to the source account/region when they differ'() {
    given:
    def replacements = ['oldPolicyARN': 'newPolicyARN']
    def actions = ['oldPolicyARN', 'sns:us-east-1', "sns:${sourceCredentials.accountId}:someQueue", 'ok-one']

    when:
    def replacedActions = scalingPolicyCopier.replacePolicyArnActions('us-east-1', 'us-west-1', sourceCredentials, targetCredentials, replacements, actions)

    then:
    replacedActions == ['ok-one', 'newPolicyARN']
  }

  void 'generates a semantically meaningful alarm name'() {
    given:
    ScalingPolicy policy = new ScalingPolicy(
      policyARN: 'oldPolicyARN1',
      autoScalingGroupName: 'asgard-v000',
      policyName: 'policy1',
      scalingAdjustment: 5,
      adjustmentType: 'ChangeInCapacity',
      cooldown: 100,
      minAdjustmentStep: 2,
      alarms: [new Alarm(alarmName: 'alarm1')]
    )
    MetricAlarm alarm = new MetricAlarm(
        alarmName: 'alarm1',
        alarmDescription: 'alarm 1 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN1'],
        insufficientDataActions: [],
        metricName: 'metric1',
        namespace: 'namespace1',
        statistic: 'statistic1',
        dimensions: [
          new Dimension(name: AsgReferenceCopier.DIMENSION_NAME_FOR_ASG, value: 'asgard-v000')
        ],
        period: 1,
        unit: 'unit1',
        evaluationPeriods: 2,
        threshold: 4.2,
        comparisonOperator: 'GreaterThanOrEqualToThreshold'
      )

    DefaultScalingPolicyCopier.PolicyNameGenerator generator = new DefaultScalingPolicyCopier.PolicyNameGenerator(idGenerator, amazonClientProvider)

    when:
    String result = generator.generateScalingPolicyName(sourceCredentials, 'us-east-1', 'asgard-v010', 'asgard-v011', policy)

    then:
    result.startsWith('asgard-v011-namespace1-metric1-GreaterThanOrEqualToThreshold-4.2-2-1-')
    1 * sourceCloudWatch.describeAlarms(new DescribeAlarmsRequest(alarmNames: ['alarm1'])) >> new DescribeAlarmsResult(metricAlarms: [
      alarm
    ])
  }

  void 'falls back to asg name replacement when no alarms found'() {
    given:
    ScalingPolicy policy = new ScalingPolicy(
      policyARN: 'oldPolicyARN1',
      autoScalingGroupName: 'asgard-v000',
      policyName: 'asgard-v010-blah-blah-blah',
      scalingAdjustment: 5,
      adjustmentType: 'ChangeInCapacity',
      cooldown: 100,
      minAdjustmentStep: 2,
      alarms: []
    )
    DefaultScalingPolicyCopier.PolicyNameGenerator generator = new DefaultScalingPolicyCopier.PolicyNameGenerator(idGenerator, amazonClientProvider)

    when:
    String result = generator.generateScalingPolicyName(sourceCredentials, 'us-east-1', 'asgard-v010', 'asgard-v011', policy)

    then:
    result == 'asgard-v011-blah-blah-blah'
  }

  void 'should copy scaling policies and alarms'() {
    when:
    scalingPolicyCopier.copyScalingPolicies(Mock(Task), 'asgard-v000', 'asgard-v001', sourceCredentials, targetCredentials, 'us-east-1', 'us-west-1')

    then:
    1 * sourceAutoScaling.describePolicies(new DescribePoliciesRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribePoliciesResult(scalingPolicies: [
        new ScalingPolicy(
          policyARN: 'oldPolicyARN1',
          autoScalingGroupName: 'asgard-v000',
          policyName: 'policy1',
          scalingAdjustment: 5,
          adjustmentType: 'ChangeInCapacity',
          cooldown: 100,
          minAdjustmentStep: 2,
          alarms: ['alarm1', 'alarm2'].collect { new Alarm(alarmName: it) }
        ),
        new ScalingPolicy(
          policyARN: 'oldPolicyARN2',
          autoScalingGroupName: 'asgard-v000',
          policyName: 'policy2',
          scalingAdjustment: 10,
          adjustmentType: 'PercentChangeInCapacity',
          cooldown: 200,
          minAdjustmentStep: 3,
          minAdjustmentMagnitude: 20,
          metricAggregationType: "Average",
          estimatedInstanceWarmup: 30,
          stepAdjustments: [
            new StepAdjustment(
              metricIntervalLowerBound: 10.5,
              metricIntervalUpperBound: 11.5,
              scalingAdjustment: 90,
            )
          ],
          policyType: "StepScaling",
          alarms: ['alarm2', 'alarm3'].collect { new Alarm(alarmName: it) }
        )
      ]
      )
    1 * targetAutoScaling.putScalingPolicy(new PutScalingPolicyRequest(
      autoScalingGroupName: 'asgard-v001',
      policyName: newPolicyName,
      scalingAdjustment: 5,
      adjustmentType: 'ChangeInCapacity',
      cooldown: 100,
      minAdjustmentStep: 2
    )) >> new PutScalingPolicyResult(policyARN: 'newPolicyARN1')
    1 * targetAutoScaling.putScalingPolicy(new PutScalingPolicyRequest(
      autoScalingGroupName: 'asgard-v001',
      policyName: newPolicyName,
      scalingAdjustment: 10,
      adjustmentType: 'PercentChangeInCapacity',
      cooldown: 200,
      minAdjustmentStep: 3,
      minAdjustmentMagnitude: 20,
      metricAggregationType: "Average",
      estimatedInstanceWarmup: 30,
      stepAdjustments: [
        new StepAdjustment(
          metricIntervalLowerBound: 10.5,
          metricIntervalUpperBound: 11.5,
          scalingAdjustment: 90,
        )
      ],
      policyType: "StepScaling",
    )) >> new PutScalingPolicyResult(policyARN: 'newPolicyARN2')

    1 * sourceCloudWatch.describeAlarms(new DescribeAlarmsRequest(alarmNames: ['alarm1', 'alarm2', 'alarm3'])) >> new DescribeAlarmsResult(metricAlarms: [
      new MetricAlarm(
        alarmName: 'alarm1',
        alarmDescription: 'alarm 1 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN1'],
        insufficientDataActions: [],
        metricName: 'metric1',
        namespace: 'namespace1',
        statistic: 'statistic1',
        dimensions: [
          new Dimension(name: AsgReferenceCopier.DIMENSION_NAME_FOR_ASG, value: 'asgard-v000')
        ],
        period: 1,
        unit: 'unit1',
        evaluationPeriods: 2,
        threshold: 4.2,
        comparisonOperator: 'GreaterThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'alarm2',
        alarmDescription: 'alarm 2 description',
        actionsEnabled: true,
        oKActions: [],
        alarmActions: ['oldPolicyARN1', 'oldPolicyARN2', 'action1'],
        insufficientDataActions: [],
        metricName: 'metric2',
        namespace: 'namespace2',
        statistic: 'statistic2',
        dimensions: [
          new Dimension(name: AsgReferenceCopier.DIMENSION_NAME_FOR_ASG, value: 'asgard-v000'),
          new Dimension(name: 'other', value: 'dimension1')
        ],
        period: 10,
        unit: 'unit2',
        evaluationPeriods: 20,
        threshold: 40.2,
        comparisonOperator: 'LessThanOrEqualToThreshold'
      ),
      new MetricAlarm(
        alarmName: 'alarm3',
        alarmDescription: 'alarm 3 description',
        actionsEnabled: false,
        oKActions: [],
        alarmActions: [],
        insufficientDataActions: ['oldPolicyARN2'],
        metricName: 'metric3',
        namespace: 'namespace3',
        statistic: 'statistic3',
        dimensions: [],
        period: 31,
        unit: 'unit3',
        evaluationPeriods: 32,
        threshold: 34,
        comparisonOperator: 'GreaterThanThreshold'
      ),
    ])
    1 * targetCloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: 'asgard-v001-alarm-1',
      alarmDescription: 'alarm 1 description',
      actionsEnabled: true,
      oKActions: [],
      alarmActions: ['newPolicyARN1'],
      insufficientDataActions: [],
      metricName: 'metric1',
      namespace: 'namespace1',
      statistic: 'statistic1',
      dimensions: [
        new Dimension(name: AsgReferenceCopier.DIMENSION_NAME_FOR_ASG, value: 'asgard-v001')
      ],
      period: 1,
      unit: 'unit1',
      evaluationPeriods: 2,
      threshold: 4.2,
      comparisonOperator: 'GreaterThanOrEqualToThreshold'
    ))
    1 * targetCloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: 'asgard-v001-alarm-2',
      alarmDescription: 'alarm 2 description',
      actionsEnabled: true,
      oKActions: [],
      alarmActions: ['action1', 'newPolicyARN1', 'newPolicyARN2'],
      insufficientDataActions: [],
      metricName: 'metric2',
      namespace: 'namespace2',
      statistic: 'statistic2',
      dimensions: [
        new Dimension(name: 'other', value: 'dimension1'),
        new Dimension(name: AsgReferenceCopier.DIMENSION_NAME_FOR_ASG, value: 'asgard-v001')
      ],
      period: 10,
      unit: 'unit2',
      evaluationPeriods: 20,
      threshold: 40.2,
      comparisonOperator: 'LessThanOrEqualToThreshold'
    ))
    1 * targetCloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: 'asgard-v001-alarm-3',
      alarmDescription: 'alarm 3 description',
      actionsEnabled: false,
      oKActions: [],
      alarmActions: [],
      insufficientDataActions: ['newPolicyARN2'],
      metricName: 'metric3',
      namespace: 'namespace3',
      statistic: 'statistic3',
      dimensions: [],
      period: 31,
      unit: 'unit3',
      evaluationPeriods: 32,
      threshold: 34,
      comparisonOperator: 'GreaterThanThreshold'
    ))
  }
}
