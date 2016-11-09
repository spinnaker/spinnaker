/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import spock.lang.Specification
import spock.lang.Subject

class AsgLifecycleHookWorkerSpec extends Specification {

  def autoScaling = Mock(AmazonAutoScaling)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScaling(_, 'us-east-1', true) >> autoScaling
  }

  int count = 0
  def idGenerator = Stub(IdGenerator) {
    nextId() >> { (++count).toString() }
  }

  @Subject def asgLifecycleHookWorker = new AsgLifecycleHookWorker(amazonClientProvider, null, 'us-east-1', idGenerator)

  void 'should no-op with no lifecycle hooks defined'() {
    when:
    asgLifecycleHookWorker.attach(Mock(Task), [], 'asg-v000')

    then:
    0 * autoScaling.putLifecycleHook(_)
  }

  void 'should create defined lifecycle hooks'() {
    given:
    def lifecycleHooks = [
      new AmazonAsgLifecycleHook(
        roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
        notificationTargetARN: 'arn:aws:sns:us-east-1:123456789012:my-sns-topic',
        lifecycleTransition: AmazonAsgLifecycleHook.LifecycleTransition.EC2InstanceTerminating,
        heartbeatTimeout: 3600,
        defaultResult: AmazonAsgLifecycleHook.DefaultResult.ABANDON
      ),
      new AmazonAsgLifecycleHook(
        roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
        notificationTargetARN: 'arn:aws:sns:{{region}}:123456789012:my-sns-topic',
        lifecycleTransition: AmazonAsgLifecycleHook.LifecycleTransition.EC2InstanceLaunching,
        heartbeatTimeout: 3600,
        defaultResult: AmazonAsgLifecycleHook.DefaultResult.CONTINUE
      )
    ]

    when:
    asgLifecycleHookWorker.attach(Mock(Task), lifecycleHooks, 'asg-v000')

    then:
    1 * autoScaling.putLifecycleHook(new PutLifecycleHookRequest(
      lifecycleHookName: 'asg-v000-lifecycle-1',
      autoScalingGroupName: 'asg-v000',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING',
      notificationTargetARN: 'arn:aws:sns:us-east-1:123456789012:my-sns-topic',
      roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
      heartbeatTimeout: 3600,
      defaultResult: 'ABANDON'
    ))
    1 * autoScaling.putLifecycleHook(new PutLifecycleHookRequest(
      lifecycleHookName: 'asg-v000-lifecycle-2',
      autoScalingGroupName: 'asg-v000',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_LAUNCHING',
      notificationTargetARN: 'arn:aws:sns:us-east-1:123456789012:my-sns-topic',
      roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
      heartbeatTimeout: 3600,
      defaultResult: 'CONTINUE'
    ))

  }
}
