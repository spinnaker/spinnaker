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
import com.amazonaws.services.autoscaling.model.Alarm
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsResult
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
import com.amazonaws.services.autoscaling.model.PutScheduledUpdateGroupActionRequest
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction
import com.amazonaws.services.autoscaling.model.StepAdjustment
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.services.IdGenerator
import com.netflix.spinnaker.kato.data.task.Task
import spock.lang.Specification
import spock.lang.Subject

class AsgReferenceCopierSpec extends Specification {

  def sourceAutoScaling = Mock(AmazonAutoScaling)
  def targetAutoScaling = Mock(AmazonAutoScaling)
  def sourceCloudWatch = Mock(AmazonCloudWatch)
  def targetCloudWatch = Mock(AmazonCloudWatch)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScaling(_, 'us-east-1', true) >> sourceAutoScaling
    getAutoScaling(_, 'us-west-1', true) >> targetAutoScaling
    getCloudWatch(_, 'us-east-1', true) >> sourceCloudWatch
    getCloudWatch(_, 'us-west-1', true) >> targetCloudWatch
  }

  long now = System.currentTimeMillis()

  int count = 0
  def idGenerator = Stub(IdGenerator) {
    nextId() >> { (++count).toString() }
  }

  @Subject def asgReferenceCopier = new AsgReferenceCopier(amazonClientProvider, null, 'us-east-1', null, 'us-west-1', idGenerator)

  void 'should copy scheduled actions'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(new DescribeScheduledActionsRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribeScheduledActionsResult(scheduledUpdateGroupActions: [
        new ScheduledUpdateGroupAction(
          autoScalingGroupName: 'asgard-v000',
          scheduledActionName: 'scheduledAction1',
          endTime: new Date(now + 1000000),
          recurrence: "0 0 1 * *",
          minSize: 1,
          maxSize: 5,
          desiredCapacity: 3
        ),
        new ScheduledUpdateGroupAction(
          autoScalingGroupName: 'asgard-v000',
          scheduledActionName: 'scheduledAction2',
          endTime: new Date(now + 1000001),
          recurrence: "0 0 1 * 0",
          minSize: 2,
          maxSize: 6,
          desiredCapacity: 4
        )
      ]
    )
    1 * targetAutoScaling.putScheduledUpdateGroupAction(new PutScheduledUpdateGroupActionRequest(
      autoScalingGroupName: 'asgard-v001',
      scheduledActionName: 'asgard-v001-schedule-1',
      endTime: new Date(now + 1000000),
      recurrence: "0 0 1 * *",
      minSize: 1,
      maxSize: 5,
      desiredCapacity: 3
    ))
    1 * targetAutoScaling.putScheduledUpdateGroupAction(new PutScheduledUpdateGroupActionRequest(
      autoScalingGroupName: 'asgard-v001',
      scheduledActionName: 'asgard-v001-schedule-2',
      endTime: new Date(now + 1000001),
      recurrence: "0 0 1 * 0",
      minSize: 2,
      maxSize: 6,
      desiredCapacity: 4
    ))
  }

  void 'should copy nothing when there are no scheduled actions'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(new DescribeScheduledActionsRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribeScheduledActionsResult(scheduledUpdateGroupActions: [])
    0 * targetAutoScaling.putScheduledUpdateGroupAction(_)
  }

  void 'should not copy scheduled action start time if older than now'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(new DescribeScheduledActionsRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribeScheduledActionsResult(scheduledUpdateGroupActions: [
        new ScheduledUpdateGroupAction(
          startTime: new Date(now - 1)
        )
      ]
      )
    1 * targetAutoScaling.putScheduledUpdateGroupAction(new PutScheduledUpdateGroupActionRequest(
      autoScalingGroupName: 'asgard-v001',
      scheduledActionName: 'asgard-v001-schedule-1'
    ))
  }

  void 'should copy scheduled action and convert time to startTime'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(new DescribeScheduledActionsRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribeScheduledActionsResult(scheduledUpdateGroupActions: [
        new ScheduledUpdateGroupAction(
          time: new Date(now  + 1000000)
        )
      ]
      )
    1 * targetAutoScaling.putScheduledUpdateGroupAction(new PutScheduledUpdateGroupActionRequest(
      autoScalingGroupName: 'asgard-v001',
      scheduledActionName: 'asgard-v001-schedule-1',
      startTime: new Date(now  + 1000000)
    ))
  }

  void 'should copy nothing when there are no scaling policies'() {
    when:
    asgReferenceCopier.copyScalingPoliciesWithAlarms(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describePolicies(new DescribePoliciesRequest(autoScalingGroupName: 'asgard-v000')) >>
      new DescribePoliciesResult(scalingPolicies: [])
    0 * targetAutoScaling.putScalingPolicy(_)
    0 * sourceCloudWatch.describeAlarms(_)
    0 * targetCloudWatch.putMetricAlarm(_)
  }

  void 'should copy scaling policies and alarms'() {
    when:
    asgReferenceCopier.copyScalingPoliciesWithAlarms(Mock(Task), 'asgard-v000', 'asgard-v001')

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
      policyName: 'asgard-v001-policy-1',
      scalingAdjustment: 5,
      adjustmentType: 'ChangeInCapacity',
      cooldown: 100,
      minAdjustmentStep: 2
    )) >> new PutScalingPolicyResult(policyARN: 'newPolicyARN1')
    1 * targetAutoScaling.putScalingPolicy(new PutScalingPolicyRequest(
      autoScalingGroupName: 'asgard-v001',
      policyName: 'asgard-v001-policy-2',
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
      alarmName: 'asgard-v001-alarm-3',
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
      alarmName: 'asgard-v001-alarm-4',
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
      alarmName: 'asgard-v001-alarm-5',
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
