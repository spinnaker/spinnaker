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

package com.netflix.spinnaker.kato.aws.deploy.ops.discovery

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

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
    TaskRepository.threadLocalTask.set(Mock(Task) {
      _ * updateStatus(_, _)
    })

    def asg = Mock(AutoScalingGroup) {
      1 * getInstances() >> description.instanceIds.collect { new Instance().withInstanceId(it) }
      0 * _._
    }
    def asgService = Mock(AsgService) {
      1 * getAutoScalingGroups([description.asgName]) >> [asg]
    }
    operation.discoverySupport = Mock(DiscoverySupport)
    operation.regionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
      1 * forRegion(_,_) >> Mock(RegionScopedProviderFactory.RegionScopedProvider) {
        1 * getAsgService() >> asgService
      }
    }

    when:
    operation.operate([])

    then:
    1 * operation.discoverySupport.updateDiscoveryStatusForInstances(
        _, _, _, expectedDiscoveryStatus, description.instanceIds
    )

    where:
    operation                                                   || expectedDiscoveryStatus
    new EnableInstancesInDiscoveryAtomicOperation(description)  || DiscoverySupport.DiscoveryStatus.Enable
    new DisableInstancesInDiscoveryAtomicOperation(description) || DiscoverySupport.DiscoveryStatus.Disable
  }
}
