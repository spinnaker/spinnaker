/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class AbstractLoadBalancerRegistrationTaskSpec extends Specification {
  @Subject task = new TestLoadBalancerRegistrationTask()
  def stage = stage()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def testLoadBalancerRegistration = [
    instanceIds      : ["some-instance-name"],
    loadBalancerNames: ["flapjack-frontend"],
    region           : "us-central1",
    credentials      : "test-account-name",
    cloudProvider    : "abc"
  ]

  def setup() {
    stage.context.putAll(testLoadBalancerRegistration)
  }

  def "creates a register instances with load balancer task based on job parameters"() {
    given:
      def operations
      task.kato = Stub(KatoService) {
        requestOperations(*_) >> {
          operations = it[1]
          rx.Observable.from(taskId)
        }
      }
      task.tsgResolver = Stub(TargetServerGroupResolver) {
        resolve(*_) >> {
          [new TargetServerGroup([name: "foo-v000"])]
        }
      }

    when:
      task.execute(stage)

    then:
      operations.size() == 1
      with(operations[0].testLoadBalancerRegistration) {
        it instanceof Map
        instanceIds == this.testLoadBalancerRegistration.instanceIds
        loadBalancerNames == this.testLoadBalancerRegistration.loadBalancerNames
        region == this.testLoadBalancerRegistration.region
        credentials == this.testLoadBalancerRegistration.credentials
        cloudProvider == this.testLoadBalancerRegistration.cloudProvider
        serverGroupName == "foo-v000"
      }
  }

  def "returns a success status with the kato task id"() {
    given:
      task.kato = Stub(KatoService) {
        requestOperations(*_) >> rx.Observable.from(taskId)
      }
      task.tsgResolver = Stub(TargetServerGroupResolver) {
        resolve() >> {
          [new TargetServerGroup([name: "foo-v000"])]
        }
      }

    when:
      def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED
      result.context."kato.last.task.id" == taskId
      result.context.interestingHealthProviderNames == ["LoadBalancer", "TargetGroup"]
  }
}

class TestLoadBalancerRegistrationTask extends AbstractLoadBalancerRegistrationTask {
  @Override
  String getAction() {
    return "testLoadBalancerRegistration"
  }
}
