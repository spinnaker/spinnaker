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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsResult
import com.amazonaws.services.autoscaling.model.PutScheduledUpdateGroupActionRequest
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
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

}
