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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.ListTopicsResult
import com.amazonaws.services.sns.model.Topic
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertScalingPolicyDescription
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertScalingPolicyAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertScalingPolicyDescription(
    name: "dansScalingPolicy",
    asgName: "kato-main-v000",
    region: "us-west-1",
    metric: new UpsertScalingPolicyDescription.Metric([
      name: "CPUUtilization",
      namespace: "AWS/EC2"
    ]),
    threshold: 81,
    scaleAmount: 1
  )

  @Subject op = new UpsertScalingPolicyAtomicOperation(description)

  @Shared
  AmazonCloudWatch cloudWatch

  @Shared
  AmazonAutoScaling autoScaling

  @Shared
  AmazonSNS sns

  def setup() {
    cloudWatch = Mock(AmazonCloudWatch)
    autoScaling = Mock(AmazonAutoScaling)
    sns = Mock(AmazonSNS)
    def amazonClientProvider = Mock(AmazonClientProvider)
    amazonClientProvider.getCloudWatch(_, _, true) >> cloudWatch
    amazonClientProvider.getAutoScaling(_, _, true) >> autoScaling
    amazonClientProvider.getAmazonSNS(_, _, true) >> sns
    op.amazonClientProvider = amazonClientProvider
  }

  void "creates the scalingPolicy based on description"() {
    when:
    op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(_) >> { PutScalingPolicyRequest request ->
      assert request.policyName == "${description.name}--${description.asgName}-${description.threshold}"
      assert request.adjustmentType == "ChangeInCapacity"
      assert request.scalingAdjustment == description.scaleAmount
      assert request.autoScalingGroupName == description.asgName
      Mock(PutScalingPolicyResult) {
        getPolicyARN() >> "foo/bar"
      }
    }
  }

  void "topics are written as actions when they are supplied"() {
    setup:
    description.scaleUpTopic = "foo"
    description.scaleDownTopic = "bar"

    when:
    op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(_) >> Mock(PutScalingPolicyResult) {
      getPolicyARN() >> "foo/bar"
    }
    2 * sns.listTopics(_) >>> [new ListTopicsResult().withTopics(new Topic().withTopicArn("arn:aws:sns:foo")),
                               new ListTopicsResult().withTopics(new Topic().withTopicArn("arn:aws:sns:bar"))]
    1 * cloudWatch.putMetricAlarm(_) >> { PutMetricAlarmRequest request ->
      assert request.alarmActions.contains("arn:aws:sns:foo")
    }
    1 * cloudWatch.putMetricAlarm(_) >> { PutMetricAlarmRequest request ->
      assert request.alarmActions.contains("arn:aws:sns:bar")
    }
  }

  void "creates the scale-up and scale-down policy based on description"() {
    when:
    op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(_) >> Mock(PutScalingPolicyResult) {
      getPolicyARN() >> "foo/bar"
    }
    1 * cloudWatch.putMetricAlarm(_) >> { PutMetricAlarmRequest request ->
      assert request.alarmName == "${description.name}-scaleUp--${description.asgName}-${description.threshold}"
      assert request.metricName == description.metric.name
      assert request.namespace == description.metric.namespace
      assert request.threshold == description.threshold
      assert request.alarmActions[0] == "foo/bar"
      (Void)null
    }
    1 * cloudWatch.putMetricAlarm(_) >> { PutMetricAlarmRequest request ->
      assert request.alarmName == "${description.name}-scaleDown--${description.asgName}-${description.threshold}"
      assert request.metricName == description.metric.name
      assert request.namespace == description.metric.namespace
      assert request.threshold == description.threshold
      assert request.alarmActions[0] == "foo/bar"
      (Void)null
    }
  }
}
