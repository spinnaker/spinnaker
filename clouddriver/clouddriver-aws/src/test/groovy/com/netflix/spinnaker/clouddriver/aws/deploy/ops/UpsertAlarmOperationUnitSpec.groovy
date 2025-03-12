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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.ComparisonOperator
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.cloudwatch.model.Statistic
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.ListTopicsResult
import com.amazonaws.services.sns.model.Topic
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAlarmDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertAlarmOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertAlarmDescription(
    region: "us-west-1",
    alarmDescription: "annoying alarm",
    comparisonOperator: ComparisonOperator.GreaterThanThreshold,
    evaluationPeriods: 1,
    period: 2,
    threshold: 10.5,
    namespace: "AWS/EC2",
    metricName: "CPUUtilization",
    statistic: Statistic.SampleCount,
    unit: StandardUnit.Percent,
  )

  def cloudWatch = Mock(AmazonCloudWatch)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getCloudWatch(_, _, true) >> cloudWatch
  }

  @Subject def op = new UpsertAlarmAtomicOperation(description)

  def setup() {
    op.amazonClientProvider = amazonClientProvider
    op.IdGenerator = new IdGenerator() {
      int nextId = 0
      String nextId() {
        ++nextId
      }
    }
  }

  void "creates unnamed alarm"() {

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "alarm-1",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent"
    ))
  }

  void "updates named alarm"() {
    description.name = "myAlarm"

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "myAlarm",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent"
    ))
  }


  void "creates alarm for ASG"() {
    description.asgName = "kato-main-v000"

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "kato-main-v000-alarm-1",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent",
      dimensions: [
              new Dimension(name: "AutoScalingGroupName", value: "kato-main-v000")
      ]
    ))
  }


  void "creates disabled alarm"() {
    description.actionsEnabled = false

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "alarm-1",
      actionsEnabled: false,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent"
    ))
  }

  void "creates alarm with dimensions"() {
    description.dimensions = [
            new Dimension(name: "a", value: "1")
    ]

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "alarm-1",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent",
      dimensions: [
        new Dimension(name: "a", value: "1")
      ]
    ))
  }

  void "creates alarm with dimensions for ASG"() {
    description.asgName = "kato-main-v000"
    description.dimensions = [
      new Dimension(name: "a", value: "1")
    ]

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "kato-main-v000-alarm-1",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent",
      dimensions: [
        new Dimension(name: "a", value: "1"),
        new Dimension(name: "AutoScalingGroupName", value: "kato-main-v000")
      ],
    ))
  }

  void "creates alarm with actions"() {
    description.alarmActionArns = ["arn1"]
    description.insufficientDataActionArns = ["arn2", "arn3"]
    description.okActionArns = ["arn4"]

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "alarm-1",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent",
      alarmActions: ["arn1"],
      insufficientDataActions: ["arn2", "arn3"],
      oKActions: ["arn4"]
    ))
  }

  void "creates alarm with associated scaling policy in prior output"() {

    when:
    op.operate([
            new UpsertScalingPolicyResult(policyArn: "arn")
    ])

    then:
    1 * cloudWatch.putMetricAlarm(new PutMetricAlarmRequest(
      alarmName: "alarm-1",
      actionsEnabled: true,
      alarmDescription: "annoying alarm",
      comparisonOperator: "GreaterThanThreshold",
      evaluationPeriods: 1,
      period: 2,
      threshold: 10.5,
      namespace: "AWS/EC2",
      metricName: "CPUUtilization",
      statistic: "SampleCount",
      unit: "Percent",
      alarmActions: [
              "arn"
      ]
    ))
  }

}
