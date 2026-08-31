/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.DescribeScheduledActionsRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeScheduledActionsResponse
import software.amazon.awssdk.services.autoscaling.model.PutScheduledUpdateGroupActionRequest
import software.amazon.awssdk.services.autoscaling.model.ScheduledUpdateGroupAction
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class AsgReferenceCopierSpec extends Specification {

  def sourceAutoScaling = Mock(AutoScalingClient)
  def targetAutoScaling = Mock(AutoScalingClient)
  def sourceCloudWatch = Mock(AmazonCloudWatch)
  def targetCloudWatch = Mock(AmazonCloudWatch)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScalingV2(_, 'us-east-1') >> sourceAutoScaling
    getAutoScalingV2(_, 'us-west-1') >> targetAutoScaling
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
    1 * sourceAutoScaling.describeScheduledActions(DescribeScheduledActionsRequest.builder().autoScalingGroupName('asgard-v000').build()) >>
      DescribeScheduledActionsResponse.builder().scheduledUpdateGroupActions([
        ScheduledUpdateGroupAction.builder()
          .autoScalingGroupName('asgard-v000')
          .scheduledActionName('scheduledAction1')
          .endTime(Instant.ofEpochMilli(now + 1000000))
          .recurrence("0 0 1 * *")
          .minSize(1)
          .maxSize(5)
          .desiredCapacity(3)
          .build(),
        ScheduledUpdateGroupAction.builder()
          .autoScalingGroupName('asgard-v000')
          .scheduledActionName('scheduledAction2')
          .endTime(Instant.ofEpochMilli(now + 1000001))
          .recurrence("0 0 1 * 0")
          .minSize(2)
          .maxSize(6)
          .desiredCapacity(4)
          .build()
      ]).build()
    1 * targetAutoScaling.putScheduledUpdateGroupAction(PutScheduledUpdateGroupActionRequest.builder()
      .autoScalingGroupName('asgard-v001')
      .scheduledActionName('asgard-v001-schedule-1')
      .endTime(Instant.ofEpochMilli(now + 1000000))
      .recurrence("0 0 1 * *")
      .minSize(1)
      .maxSize(5)
      .desiredCapacity(3)
      .build())
    1 * targetAutoScaling.putScheduledUpdateGroupAction(PutScheduledUpdateGroupActionRequest.builder()
      .autoScalingGroupName('asgard-v001')
      .scheduledActionName('asgard-v001-schedule-2')
      .endTime(Instant.ofEpochMilli(now + 1000001))
      .recurrence("0 0 1 * 0")
      .minSize(2)
      .maxSize(6)
      .desiredCapacity(4)
      .build())
  }

  void 'should copy nothing when there are no scheduled actions'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(DescribeScheduledActionsRequest.builder().autoScalingGroupName('asgard-v000').build()) >>
      DescribeScheduledActionsResponse.builder().scheduledUpdateGroupActions([]).build()
    0 * targetAutoScaling.putScheduledUpdateGroupAction(_)
  }

  void 'should not copy scheduled action start time if older than now'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(DescribeScheduledActionsRequest.builder().autoScalingGroupName('asgard-v000').build()) >>
      DescribeScheduledActionsResponse.builder().scheduledUpdateGroupActions([
        ScheduledUpdateGroupAction.builder()
          .startTime(Instant.ofEpochMilli(now - 1))
          .build()
      ]).build()
    1 * targetAutoScaling.putScheduledUpdateGroupAction(PutScheduledUpdateGroupActionRequest.builder()
      .autoScalingGroupName('asgard-v001')
      .scheduledActionName('asgard-v001-schedule-1')
      .build())
  }

  void 'should copy scheduled action and convert time to startTime'() {
    when:
    asgReferenceCopier.copyScheduledActionsForAsg(Mock(Task), 'asgard-v000', 'asgard-v001')

    then:
    1 * sourceAutoScaling.describeScheduledActions(DescribeScheduledActionsRequest.builder().autoScalingGroupName('asgard-v000').build()) >>
      DescribeScheduledActionsResponse.builder().scheduledUpdateGroupActions([
        ScheduledUpdateGroupAction.builder()
          .time(Instant.ofEpochMilli(now + 1000000))
          .build()
      ]).build()
    1 * targetAutoScaling.putScheduledUpdateGroupAction(PutScheduledUpdateGroupActionRequest.builder()
      .autoScalingGroupName('asgard-v001')
      .scheduledActionName('asgard-v001-schedule-1')
      .startTime(Instant.ofEpochMilli(now + 1000000))
      .build())
  }

}
