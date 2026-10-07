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
package com.netflix.spinnaker.clouddriver.aws.services
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.model.AwsNetworkInterface
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.model.TagsNotCreatedException
import com.netflix.spinnaker.clouddriver.aws.services.NetworkInterfaceService
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import spock.lang.Specification

class NetworkInterfaceServiceSpec extends Specification {

  def networkInterfaceService = new NetworkInterfaceService(
    Mock(SecurityGroupService),
    Mock(SubnetAnalyzer),
    Mock(Ec2Client))

  CreateNetworkInterfaceRequest createCommonNetworkInterfaceRequest() {
    CreateNetworkInterfaceRequest.builder()
      .subnetId("subnet-12345678")
      .description("internal Asgard")
      .privateIpAddress("127.0.0.1")
      .groups(["sg-12345678"])
      .privateIpAddresses([
        PrivateIpAddressSpecification.builder().privateIpAddress("127.0.0.2").primary(false).build(),
        PrivateIpAddressSpecification.builder().privateIpAddress("127.0.0.3").primary(false).build()
      ])
      .build()
  }

  CreateNetworkInterfaceResponse createNetworkInterfaceWithId(String id) {
    CreateNetworkInterfaceResponse.builder().networkInterface(NetworkInterface.builder().networkInterfaceId(id).build()).build()
  }

  def networkInterface = new AwsNetworkInterface(
    description: "internal Asgard",
    primaryPrivateIpAddress: "127.0.0.1",
    securityGroupNames: ["asgard"],
    secondaryPrivateIpAddresses: ["127.0.0.2", "127.0.0.3"],
    tags: [
    type: "webserver",
    stack: "production"
    ]
  )

  void "should create network interface"() {
    when:
    NetworkInterface result = networkInterfaceService.createNetworkInterface("us-east-1a", "internal", networkInterface)

    then:
    result == NetworkInterface.builder().networkInterfaceId("new ENI").build()

    and:
    with(networkInterfaceService.securityGroupService) {
      1 * getSecurityGroupIds(["asgard"], 'vpc-1234') >> [asgard: "sg-12345678"]
    }
    with(networkInterfaceService.subnetAnalyzer) {
      1 * getVpcIdForSubnetPurpose('internal') >> "vpc-1234"
      1 * getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB) >> ["subnet-12345678"]
    }
    with(networkInterfaceService.amazonEC2) {
      1 * createNetworkInterface(createCommonNetworkInterfaceRequest()) >> createNetworkInterfaceWithId("new ENI")
      1 * createTags(CreateTagsRequest.builder().resources(["new ENI"]).tags([
        Tag.builder().key("type").value("webserver").build(),
        Tag.builder().key("stack").value("production").build()
      ]).build())
    }
    0 * _
  }

  void "should fail for create Network Interface error"() {
    when:
    networkInterfaceService.createNetworkInterface("us-east-1a", "internal", networkInterface)

    then:
    Exception e = thrown()
    e.cause.message == "Uh Oh!"

    and:
    with(networkInterfaceService.securityGroupService) {
      1 * getSecurityGroupIds(["asgard"], 'vpc-1234') >> [asgard: "sg-12345678"]
    }
    with(networkInterfaceService.subnetAnalyzer) {
      1 * getVpcIdForSubnetPurpose('internal') >> "vpc-1234"
      1 * getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB) >> ["subnet-12345678"]
    }
    with(networkInterfaceService.amazonEC2) {
      1 * createNetworkInterface(createCommonNetworkInterfaceRequest()) >> {
        throw new Exception("Uh Oh!")
      }
    }
    0 * _
  }

  void "should fail for create Tags error with TagsNotCreatedException"() {
    when:
    networkInterfaceService.createNetworkInterface("us-east-1a", "internal", networkInterface)

    then:
    TagsNotCreatedException e = thrown()
    e.cause.cause.message == "Uh Oh!"
    e.objectToTag == NetworkInterface.builder().networkInterfaceId("new ENI").build()

    and:
    with(networkInterfaceService.securityGroupService) {
      1 * getSecurityGroupIds(["asgard"], 'vpc-1234') >> [asgard: "sg-12345678"]
    }
    with(networkInterfaceService.subnetAnalyzer) {
      1 * getVpcIdForSubnetPurpose('internal') >> "vpc-1234"
      1 * getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB) >> ["subnet-12345678"]
    }
    with(networkInterfaceService.amazonEC2) {
      1 * createNetworkInterface(createCommonNetworkInterfaceRequest()) >> createNetworkInterfaceWithId("new ENI")
      1 * createTags(CreateTagsRequest.builder().resources(["new ENI"]).tags([
        Tag.builder().key("type").value("webserver").build(),
        Tag.builder().key("stack").value("production").build()
      ]).build()) >> {
        throw new Exception("Uh Oh!")
      }
    }
    0 * _
  }

}
