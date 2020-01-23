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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.amazonaws.services.autoscaling.model.LifecycleState.*

class EnableDisableInstancesInDiscoveryAtomicOperationUnitSpec extends Specification {
  @Shared
  def description = new EnableDisableInstanceDiscoveryDescription([
    asgName    : "kato-main-v000",
    region     : "us-west-1",
    credentials: TestCredential.named('test', [discovery: 'http://%s.discovery.netflix.net']),
    instanceIds: ["i-123456"]
  ])

  @Unroll
  void "should enable/disable instances in discovery"() {
    setup:
    TaskRepository.threadLocalTask.set(Stub(Task))

    def asg = Stub(AutoScalingGroup) {
      getInstances() >> description.instanceIds.collect {
        new Instance().withInstanceId(it).withLifecycleState(InService)
      }
    }
    def asgService = Stub(AsgService) {
      getAutoScalingGroups([description.asgName]) >> [asg]
    }
    operation.discoverySupport = Mock(AwsEurekaSupport)
    operation.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProvider) {
        getAsgService() >> asgService
      }
    }

    when:
    operation.operate([])

    then:
    1 * operation.discoverySupport.updateDiscoveryStatusForInstances(
      _, _, _, expectedDiscoveryStatus, description.instanceIds, true
    )

    where:
    operation                                                   || expectedDiscoveryStatus
    new EnableInstancesInDiscoveryAtomicOperation(description)  || DiscoveryStatus.Enable
    new DisableInstancesInDiscoveryAtomicOperation(description) || DiscoveryStatus.Disable
  }

  @Unroll
  void "should not enable instances in discovery if they are in the #lifecycleState state"() {
    setup:
    TaskRepository.threadLocalTask.set(Stub(Task))

    def asg = Stub(AutoScalingGroup) {
      getInstances() >> description.instanceIds.collect {
        new Instance().withInstanceId(it).withLifecycleState(lifecycleState)
      }
    }
    def asgService = Stub(AsgService) {
      getAutoScalingGroups([description.asgName]) >> [asg]
    }
    def operation = new EnableInstancesInDiscoveryAtomicOperation(description)
    operation.discoverySupport = Mock(AwsEurekaSupport)
    operation.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProvider) {
        getAsgService() >> asgService
      }
    }

    when:
    operation.operate([])

    then:
    0 * operation.discoverySupport.updateDiscoveryStatusForInstances(*_)

    where:
    lifecycleState     | _
    Terminated         | _
    Terminating        | _
    TerminatingProceed | _
    TerminatingWait    | _
    Quarantined        | _
    Detached           | _
    Detaching          | _
    EnteringStandby    | _
    Standby            | _
  }
}
