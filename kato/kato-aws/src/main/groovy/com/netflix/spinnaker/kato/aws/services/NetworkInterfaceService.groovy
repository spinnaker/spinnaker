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
package com.netflix.spinnaker.kato.aws.services

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.google.common.collect.Iterables
import com.netflix.spinnaker.kato.aws.model.AwsNetworkInterface
import com.netflix.spinnaker.kato.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.kato.aws.model.SubnetTarget
import com.netflix.spinnaker.kato.aws.model.TagsNotCreatedException
import groovy.transform.Canonical

@Canonical
class NetworkInterfaceService {

  final SecurityGroupService securityGroupService
  final SubnetAnalyzer subnetAnalyzer
  final AmazonEC2 amazonEC2

  NetworkInterface createNetworkInterface(String availabilityZone, String subnetPurpose, AwsNetworkInterface networkInterface) {
    def vpcId = subnetAnalyzer.getVpcIdForSubnetPurpose(subnetPurpose)
    List<String> subnetIds = subnetAnalyzer.getSubnetIdsForZones([availabilityZone], subnetPurpose, SubnetTarget.ELB)
    String subnetId = Iterables.getOnlyElement(subnetIds)
    CreateNetworkInterfaceRequest request = new CreateNetworkInterfaceRequest(
      subnetId: subnetId,
      description: networkInterface.description,
      privateIpAddress: networkInterface.primaryPrivateIpAddress,
      groups: vpcId ? securityGroupService.getSecurityGroupIds(networkInterface.securityGroupNames, vpcId).values() : securityGroupService.getSecurityGroupIds(networkInterface.securityGroupNames).values(),
      privateIpAddresses: networkInterface.secondaryPrivateIpAddresses.collect {
        new PrivateIpAddressSpecification(privateIpAddress: it, primary: false)
      },
      secondaryPrivateIpAddressCount: networkInterface.secondaryPrivateIpAddressCount
    )
    CreateNetworkInterfaceResult result = amazonEC2.createNetworkInterface(request)
    try {
      CreateTagsRequest tagRequest = new CreateTagsRequest(
        resources: [result.networkInterface.networkInterfaceId],
        tags: networkInterface.tags.collect { new Tag(key: it.key, value: it.value) }
      )
      amazonEC2.createTags(tagRequest)
    } catch (Exception createTagsException) {
      throw TagsNotCreatedException.of(createTagsException, result.networkInterface)
    }
    result.networkInterface
  }
}
