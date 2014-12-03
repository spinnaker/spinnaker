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
import com.amazonaws.services.ec2.model.NetworkInterface
import com.google.common.collect.Iterables
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.CreateNetworkInterfaceDescription
import com.netflix.spinnaker.kato.aws.model.AwsNetworkInterface
import com.netflix.spinnaker.kato.aws.model.ResultByZone
import com.netflix.spinnaker.kato.aws.model.TagsNotCreatedException
import com.netflix.spinnaker.kato.aws.services.NetworkInterfaceService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class CreateNetworkInterfaceAtomicOperationUnitSpec extends Specification {

  def mockRegionScopedProviderFactory = Mock(RegionScopedProviderFactory)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def networkInterfaceTemplate = new AwsNetworkInterface(
    description: "internal Asgard",
    securityGroupNames: ["sg-12345678", "sg-87654321"],
    primaryPrivateIpAddress: "127.0.0.1",
    secondaryPrivateIpAddresses: ["127.0.0.2", "127.0.0.3"]
  )

  void "operation invokes create network interface across availability zones"() {
    def mockNetworkInterfaceServiceWest = Mock(NetworkInterfaceService)
    def mockNetworkInterfaceServiceEast = Mock(NetworkInterfaceService)
    def description = new CreateNetworkInterfaceDescription(
      availabilityZonesGroupedByRegion: [
        "us-west-1": ["us-west-1a", "us-west-1b"],
        "us-east-1": ["us-east-1b", "us-east-1c"]
      ],
      vpcId: "vpc-1badd00d",
      subnetType: "internal",
      networkInterface: new AwsNetworkInterface(
        description: "internal Asgard",
        securityGroupNames: ["sg-12345678", "sg-87654321"],
        primaryPrivateIpAddress: "127.0.0.1",
        secondaryPrivateIpAddresses: ["127.0.0.2", "127.0.0.3"]
      ),
      credentials: TestCredential.named('baz')
    )
    def operation = new CreateNetworkInterfaceAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    ResultByZone<NetworkInterface> result = operation.operate([])

    then:
    result == ResultByZone.of([
      "us-west-1a": new NetworkInterface(networkInterfaceId: "1"),
      "us-west-1b": new NetworkInterface(networkInterfaceId: "2"),
      "us-east-1b": new NetworkInterface(networkInterfaceId: "3"),
      "us-east-1c": new NetworkInterface(networkInterfaceId: "4")
    ], [:])

    and:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-west-1") >> Mock(RegionScopedProviderFactory.RegionScopedProvider) {
      1 * getNetworkInterfaceService() >> mockNetworkInterfaceServiceWest
    }
    1 * mockNetworkInterfaceServiceWest.createNetworkInterface("us-west-1a", "internal", networkInterfaceTemplate) >> new NetworkInterface(networkInterfaceId: "1")
    1 * mockNetworkInterfaceServiceWest.createNetworkInterface("us-west-1b", "internal", networkInterfaceTemplate) >> new NetworkInterface(networkInterfaceId: "2")

    1 * mockRegionScopedProviderFactory.forRegion(_, "us-east-1") >> Mock(RegionScopedProviderFactory.RegionScopedProvider) {
      1 * getNetworkInterfaceService() >> mockNetworkInterfaceServiceEast
    }
    1 * mockNetworkInterfaceServiceEast.createNetworkInterface("us-east-1b", "internal", networkInterfaceTemplate) >> new NetworkInterface(networkInterfaceId: "3")
    1 * mockNetworkInterfaceServiceEast.createNetworkInterface("us-east-1c", "internal", networkInterfaceTemplate) >> new NetworkInterface(networkInterfaceId: "4")
    0 * _
  }

  void "operation handles exceptions creating ENI and continues for all availability zones"() {
    def mockNetworkInterfaceService = Mock(NetworkInterfaceService)
    def description = new CreateNetworkInterfaceDescription(
      availabilityZonesGroupedByRegion: [
        "us-west-1": ["us-west-1a", "us-west-1b"]
      ],
      vpcId: "vpc-1badd00d",
      subnetType: "internal",
      networkInterface: new AwsNetworkInterface(
        description: "internal Asgard",
        securityGroupNames: ["sg-12345678", "sg-87654321"],
        primaryPrivateIpAddress: "127.0.0.1",
        secondaryPrivateIpAddresses: ["127.0.0.2", "127.0.0.3"]
      ),
      credentials: TestCredential.named('baz')
    )
    def operation = new CreateNetworkInterfaceAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    ResultByZone<NetworkInterface> result = operation.operate([])

    then:
    result.successfulResults == [
      "us-west-1b": new NetworkInterface(networkInterfaceId: "2")
    ]
    Iterables.getOnlyElement(result.failures.keySet()) == "us-west-1a"
    result.failures["us-west-1a"].startsWith("java.lang.Exception: Uh oh!")

    and:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-west-1") >> Mock(RegionScopedProviderFactory.RegionScopedProvider) {
      1 * getNetworkInterfaceService() >> mockNetworkInterfaceService
    }
    1 * mockNetworkInterfaceService.createNetworkInterface("us-west-1a", "internal", networkInterfaceTemplate) >> {
      throw new Exception("Uh oh!")
    }
    1 * mockNetworkInterfaceService.createNetworkInterface("us-west-1b", "internal", networkInterfaceTemplate) >> new NetworkInterface(networkInterfaceId: "2")
    0 * _
  }

  void "operation logs tagging exceptions"() {
    def mockNetworkInterfaceService = Mock(NetworkInterfaceService)
    def description = new CreateNetworkInterfaceDescription(
      availabilityZonesGroupedByRegion: [
        "us-west-1": ["us-west-1a", "us-west-1b"]
      ],
      vpcId: "vpc-1badd00d",
      subnetType: "internal",
      networkInterface: new AwsNetworkInterface(
        description: "internal Asgard",
        securityGroupNames: ["sg-12345678", "sg-87654321"],
        primaryPrivateIpAddress: "127.0.0.1",
        secondaryPrivateIpAddresses: ["127.0.0.2", "127.0.0.3"]
      ),
      credentials: TestCredential.named('baz')
    )
    def operation = new CreateNetworkInterfaceAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    ResultByZone<NetworkInterface> result = operation.operate([])

    then:
    result == ResultByZone.of([
      "us-west-1a": new NetworkInterface(networkInterfaceId: "1"),
      "us-west-1b": new NetworkInterface(networkInterfaceId: "2")
    ], [:])

    and:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-west-1") >> Mock(RegionScopedProviderFactory.RegionScopedProvider) {
      1 * getNetworkInterfaceService() >> mockNetworkInterfaceService
    }
    1 * mockNetworkInterfaceService.createNetworkInterface("us-west-1a", "internal", networkInterfaceTemplate) >> {
      throw TagsNotCreatedException.of(new Exception("No tags for you!"), new NetworkInterface(networkInterfaceId: "1"))
    }
    1 * mockNetworkInterfaceService.createNetworkInterface("us-west-1b", "internal", networkInterfaceTemplate) >> new NetworkInterface(networkInterfaceId: "2")
    0 * _
  }
}
