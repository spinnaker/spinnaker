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
import com.amazonaws.services.autoscaling.model.AutoScalingGroup

class AbstractEnableDisableAtomicOperationUnitSpec extends EnableDisableAtomicOperationUnitSpecSupport {

  def setupSpec() {
    op = new EnableAsgAtomicOperation(description)
  }

  void 'should log unknown asgs'() {
    when:
    op.operate([])

    then:
    1 * task.updateStatus(_, 'No ASG named \'kato-main-v000\' found in us-west-1')
  }

  void 'should do nothing without instances'() {
    setup:
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(_) >> asg
    0 * loadBalancing._
  }

  void 'task should fail if an exception occurs during handling'() {
    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(_) >> { throw new RuntimeException('Boom goes the dynamite') }
    1 * task.fail()
  }
}
