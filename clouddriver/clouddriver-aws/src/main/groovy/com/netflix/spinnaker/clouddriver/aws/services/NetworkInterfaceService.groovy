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
import com.google.common.collect.Iterables
import com.netflix.spinnaker.clouddriver.aws.model.AwsNetworkInterface
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.model.TagsNotCreatedException
import groovy.transform.Canonical

@Canonical
class NetworkInterfaceService {

  final SecurityGroupService securityGroupService
  final SubnetAnalyzer subnetAnalyzer
  final Ec2Client amazonEC2

  NetworkInterface createNetworkInterface(String availabilityZone, String subnetPurpose, AwsNetworkInterface networkInterface) {
    def vpcId = subnetAnalyzer.getVpcIdForSubnetPurpose(subnetPurpose)
    List<String> subnetIds = subnetAnalyzer.getSubnetIdsForZones([availabilityZone], subnetPurpose, SubnetTarget.ELB)
    String subnetId = Iterables.getOnlyElement(subnetIds)
    CreateNetworkInterfaceRequest request = CreateNetworkInterfaceRequest.builder()
      .subnetId(subnetId)
      .description(networkInterface.description)
      .privateIpAddress(networkInterface.primaryPrivateIpAddress)
      .groups(vpcId ? securityGroupService.getSecurityGroupIds(networkInterface.securityGroupNames, vpcId).values() : securityGroupService.getSecurityGroupIds(networkInterface.securityGroupNames).values())
      .privateIpAddresses(networkInterface.secondaryPrivateIpAddresses.collect {
        PrivateIpAddressSpecification.builder().privateIpAddress(it).primary(false).build()
      })
      .secondaryPrivateIpAddressCount(networkInterface.secondaryPrivateIpAddressCount)
      .build()
    CreateNetworkInterfaceResponse result = amazonEC2.createNetworkInterface(request)
    try {
      CreateTagsRequest tagRequest = CreateTagsRequest.builder()
        .resources([result.networkInterface().networkInterfaceId()])
        .tags(networkInterface.tags.collect { Tag.builder().key(it.key).value(it.value).build() })
        .build()
      amazonEC2.createTags(tagRequest)
    } catch (Exception createTagsException) {
      throw TagsNotCreatedException.of(createTagsException, result.networkInterface())
    }
    result.networkInterface()
  }
}
