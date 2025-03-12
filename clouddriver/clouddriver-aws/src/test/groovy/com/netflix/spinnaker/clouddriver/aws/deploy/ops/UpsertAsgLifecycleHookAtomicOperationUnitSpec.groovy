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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgLifecycleHookDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class UpsertAsgLifecycleHookAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertAsgLifecycleHookDescription(
    serverGroupName: 'asg-v000',
    region: 'us-west-1',
    roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
    notificationTargetARN: 'arn:aws:sns:us-west-1:123456789012:my-sns-topic'
  )

  @Subject def op = new UpsertAsgLifecycleHookAtomicOperation(description)

  def autoScaling = Mock(AmazonAutoScaling)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScaling(_, _, true) >> autoScaling
  }

  def setup() {
    op.amazonClientProvider = amazonClientProvider
    op.idGenerator = new IdGenerator() {
      int nextId = 0
      String nextId() {
        ++nextId
      }
    }
  }

  def 'creates unnamed lifecycle hook'() {
    when:
    op.operate([])

    then:
    1 * autoScaling.putLifecycleHook(new PutLifecycleHookRequest(
      lifecycleHookName: 'asg-v000-lifecycle-1',
      autoScalingGroupName: 'asg-v000',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING',
      roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
      notificationTargetARN: 'arn:aws:sns:us-west-1:123456789012:my-sns-topic',
      heartbeatTimeout: 3600,
      defaultResult: 'ABANDON'
    ))
    0 * _
  }

  def 'removes invalid characters from lifecycle hook name'() {
    when:
    description.serverGroupName = 'a+b=c!d@e#f$g%h&i*j(k)l_m-n{o}p[q]r\\s<t>u,v.w?x/y|z-v001' // this is a valid ASG name
    op.operate([])

    then:
    1 * autoScaling.putLifecycleHook(new PutLifecycleHookRequest(
      lifecycleHookName: 'a_b_c_d_e_f_g_h_i_j_k_l_m-n_o_p_q_r_s_t_u_v_w_x/y_z-v001-lifecycle-1',
      autoScalingGroupName: 'a+b=c!d@e#f$g%h&i*j(k)l_m-n{o}p[q]r\\s<t>u,v.w?x/y|z-v001',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING',
      roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
      notificationTargetARN: 'arn:aws:sns:us-west-1:123456789012:my-sns-topic',
      heartbeatTimeout: 3600,
      defaultResult: 'ABANDON'
    ))
    0 * _
  }

  def 'creates named lifecycle hook'() {
    given:
    description.name = 'fancyHook'

    when:
    op.operate([])

    then:
    1 * autoScaling.putLifecycleHook(new PutLifecycleHookRequest(
      lifecycleHookName: 'fancyHook',
      autoScalingGroupName: 'asg-v000',
      lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING',
      roleARN: 'arn:aws:iam::123456789012:role/my-notification-role',
      notificationTargetARN: 'arn:aws:sns:us-west-1:123456789012:my-sns-topic',
      heartbeatTimeout: 3600,
      defaultResult: 'ABANDON'
    ))
    0 * _
  }
}
