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

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.cloudwatch.model.Statistic
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
    comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
    evaluationPeriods: 1,
    period: 2,
    threshold: 10.5,
    namespace: "AWS/EC2",
    metricName: "CPUUtilization",
    statistic: Statistic.SAMPLE_COUNT,
    unit: StandardUnit.PERCENT,
  )

  def cloudWatch = Mock(CloudWatchClient)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonCloudWatchV2(_, _) >> cloudWatch
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
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("alarm-1")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .build())
  }

  void "updates named alarm"() {
    description.name = "myAlarm"

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("myAlarm")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .build())
  }


  void "creates alarm for ASG"() {
    description.asgName = "kato-main-v000"

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("kato-main-v000-alarm-1")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .dimensions([
              Dimension.builder().name("AutoScalingGroupName").value("kato-main-v000").build()
      ])
      .build())
  }


  void "creates disabled alarm"() {
    description.actionsEnabled = false

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("alarm-1")
      .actionsEnabled(false)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .build())
  }

  void "creates alarm with dimensions"() {
    description.dimensions = [
            Dimension.builder().name("a").value("1").build()
    ]

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("alarm-1")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .dimensions([
        Dimension.builder().name("a").value("1").build()
      ])
      .build())
  }

  void "creates alarm with dimensions for ASG"() {
    description.asgName = "kato-main-v000"
    description.dimensions = [
      Dimension.builder().name("a").value("1").build()
    ]

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("kato-main-v000-alarm-1")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .dimensions([
        Dimension.builder().name("a").value("1").build(),
        Dimension.builder().name("AutoScalingGroupName").value("kato-main-v000").build()
      ])
      .build())
  }

  void "creates alarm with actions"() {
    description.alarmActionArns = ["arn1"]
    description.insufficientDataActionArns = ["arn2", "arn3"]
    description.okActionArns = ["arn4"]

    when:
    op.operate([])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("alarm-1")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .alarmActions(["arn1"])
      .insufficientDataActions(["arn2", "arn3"])
      .okActions(["arn4"])
      .build())
  }

  void "creates alarm with associated scaling policy in prior output"() {

    when:
    op.operate([
            new UpsertScalingPolicyResult(policyArn: "arn")
    ])

    then:
    1 * cloudWatch.putMetricAlarm(PutMetricAlarmRequest.builder()
      .alarmName("alarm-1")
      .actionsEnabled(true)
      .alarmDescription("annoying alarm")
      .comparisonOperator("GreaterThanThreshold")
      .evaluationPeriods(1)
      .period(2)
      .threshold(10.5)
      .namespace("AWS/EC2")
      .metricName("CPUUtilization")
      .statistic("SampleCount")
      .unit("Percent")
      .alarmActions([
              "arn"
      ])
      .build())
  }

}
