/*
 * Copyright 2015 Netflix, Inc.
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
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsResult
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgScheduledActionsDescription
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import spock.lang.Specification

class UpsertAsgScheduledActionsAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(new DefaultTask("taskId"))
  }

  void "creates a new scheduled action for each supplied input"() {
    setup:
    def mockAutoScalingA = Mock(AmazonAutoScaling)
    def mockAutoScalingB = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, 'us-east-1', true) >> mockAutoScalingA
    mockAmazonClientProvider.getAutoScaling(_, 'us-west-1', true) >> mockAutoScalingB
    def description = new UpsertAsgScheduledActionsDescription(
        asgs: [
            new AsgDescription(asgName: 'asg-v001', region: 'us-east-1'),
            new AsgDescription(asgName: 'asg-v002', region: 'us-west-1'),
        ],
        scheduledActions: [
            new UpsertAsgScheduledActionsDescription.ScheduledActionDescription(recurrence: '* 0 0 0 0', minSize: 1, maxSize: 1, desiredCapacity: 1),
            new UpsertAsgScheduledActionsDescription.ScheduledActionDescription(recurrence: '* 4 0 0 0', minSize: 1, maxSize: 3, desiredCapacity: 2),
        ]
    )
    description.credentials = TestCredential.named('baz')

    def operation = new UpsertAsgScheduledActionsOperation(description)
    operation.idGenerator = Stub(IdGenerator, {
      nextId() >> 'abc'
    })
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScalingA.describeAutoScalingGroups({ it.autoScalingGroupNames == ['asg-v001']}) >> {
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> "asg-v001"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * mockAutoScalingB.describeAutoScalingGroups({ it.autoScalingGroupNames == ['asg-v002']}) >> {
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> "asg-v002"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * mockAutoScalingA.describeScheduledActions(_) >> {
      new DescribeScheduledActionsResult().withScheduledUpdateGroupActions([])
    }
    1 * mockAutoScalingB.describeScheduledActions(_) >> {
      new DescribeScheduledActionsResult().withScheduledUpdateGroupActions([])
    }
    1 * mockAutoScalingA.putScheduledUpdateGroupAction({
      it.recurrence == '* 0 0 0 0' && it.minSize == 1 && it.maxSize == 1 && it.desiredCapacity == 1 && it.scheduledActionName == 'asg-v001-abc'
    })
    1 * mockAutoScalingA.putScheduledUpdateGroupAction({
      it.recurrence == '* 4 0 0 0' && it.minSize == 1 && it.maxSize == 3 && it.desiredCapacity == 2 && it.scheduledActionName == 'asg-v001-abc'
    })
    1 * mockAutoScalingB.putScheduledUpdateGroupAction({
      it.recurrence == '* 0 0 0 0' && it.minSize == 1 && it.maxSize == 1 && it.desiredCapacity == 1 && it.scheduledActionName == 'asg-v002-abc'
    })
    1 * mockAutoScalingB.putScheduledUpdateGroupAction({
      it.recurrence == '* 4 0 0 0' && it.minSize == 1 && it.maxSize == 3 && it.desiredCapacity == 2 && it.scheduledActionName == 'asg-v002-abc'
    })
  }

  void "deletes any existing scheduled actions if no new actions have the same schedule"() {
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, 'us-east-1', true) >> mockAutoScaling
    def description = new UpsertAsgScheduledActionsDescription(
        asgs: [
            new AsgDescription(asgName: 'asg-v001', region: 'us-east-1'),
        ],
        scheduledActions: []
    )
    description.credentials = TestCredential.named('baz')

    def operation = new UpsertAsgScheduledActionsOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups({ it.autoScalingGroupNames == ['asg-v001']}) >> {
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> "asg-v001"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * mockAutoScaling.describeScheduledActions(_) >> {
      new DescribeScheduledActionsResult().withScheduledUpdateGroupActions([
          new ScheduledUpdateGroupAction(scheduledActionName: 'action-1', autoScalingGroupName: 'asg-v001'),
          new ScheduledUpdateGroupAction(scheduledActionName: 'action-2', autoScalingGroupName: 'asg-v001'),
      ])
    }

    1 * mockAutoScaling.deleteScheduledAction({
      it.scheduledActionName == 'action-1' && it.autoScalingGroupName == 'asg-v001'
    })
    1 * mockAutoScaling.deleteScheduledAction({
      it.scheduledActionName == 'action-2' && it.autoScalingGroupName == 'asg-v001'
    })
  }

  void "updates any existing scheduled actions if they have the same recurrence"() {
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, 'us-east-1', true) >> mockAutoScaling
    def description = new UpsertAsgScheduledActionsDescription(
        asgs: [
            new AsgDescription(asgName: 'asg-v001', region: 'us-east-1'),
        ],
        scheduledActions: [
            new UpsertAsgScheduledActionsDescription.ScheduledActionDescription(recurrence: '40 20 * * *', minSize: 1, maxSize: 30, desiredCapacity: 1),
            new UpsertAsgScheduledActionsDescription.ScheduledActionDescription(recurrence: '* 0 0 0 0', minSize: 1, maxSize: 1, desiredCapacity: 1),
        ]
    )
    description.credentials = TestCredential.named('baz')

    def operation = new UpsertAsgScheduledActionsOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider
    operation.idGenerator = Stub(IdGenerator, {
      nextId() >> 'abc'
    })

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups({ it.autoScalingGroupNames == ['asg-v001']}) >> {
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> "asg-v001"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * mockAutoScaling.describeScheduledActions(_) >> {
      new DescribeScheduledActionsResult().withScheduledUpdateGroupActions([
          new ScheduledUpdateGroupAction(recurrence: "40 20 * * *", scheduledActionName: 'action-1', autoScalingGroupName: 'asg-v001'),
          new ScheduledUpdateGroupAction(recurrence: "20 20 * * *",scheduledActionName: 'action-2', autoScalingGroupName: 'asg-v001'),
      ])
    }

    1 * mockAutoScaling.putScheduledUpdateGroupAction({
      it.recurrence == '40 20 * * *' && it.scheduledActionName == 'action-1' && it.minSize == 1 && it.maxSize == 30 && it.desiredCapacity == 1
    })

    1 * mockAutoScaling.putScheduledUpdateGroupAction({
      it.recurrence == '* 0 0 0 0' && it.scheduledActionName == 'asg-v001-abc' && it.minSize == 1 && it.maxSize == 1 && it.desiredCapacity == 1
    })

    0 * mockAutoScaling.deleteScheduledAction({
      it.scheduledActionName == 'action-1' && it.autoScalingGroupName == 'asg-v001'
    })
    1 * mockAutoScaling.deleteScheduledAction({
      it.scheduledActionName == 'action-2' && it.autoScalingGroupName == 'asg-v001'
    })

  }
}
