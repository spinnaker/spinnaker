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
package com.netflix.spinnaker.kato.aws.model

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Immutable

/**
 * Immutable Wrapper for an AWS Subnet.
 * Metadata in tags becomes proper attributes here.
 * {@link com.amazonaws.services.ec2.model.Subnet}
 */
@Immutable
class SubnetData {

  static final String METADATA_TAG_KEY = 'immutable_metadata'

  /** {@link com.amazonaws.services.ec2.model.Subnet#subnetId} */
  String subnetId

  /** {@link com.amazonaws.services.ec2.model.Subnet#state} */
  String state

  /** {@link com.amazonaws.services.ec2.model.Subnet#vpcId} */
  String vpcId

  /** {@link com.amazonaws.services.ec2.model.Subnet#cidrBlock} */
  String cidrBlock

  /** {@link com.amazonaws.services.ec2.model.Subnet#availableIpAddressCount} */
  Integer availableIpAddressCount

  /** {@link com.amazonaws.services.ec2.model.Subnet#availabilityZone} */
  String availabilityZone

  /** A label that indicates the purpose of this Subnet's configuration. */
  String purpose

  /** The target the subnet applies to (null means any object type). */
  SubnetTarget target

  /**
   * Construct SubnetData from the original AWS Subnet
   *
   * @param subnet the mutable AWS Subnet
   * @return a new immutable SubnetData based off values from subnet
   */
  static SubnetData from(Subnet subnet) {
    Tag tag = subnet.tags.find { Tag tag ->
      tag.key == METADATA_TAG_KEY
    }
    String json = tag?.value
    ObjectMapper objectMapper = new ObjectMapper()

    String purpose = null
    SubnetTarget target = null
    if (json) {
      SubnetMetaData subnetMetaData = objectMapper.readValue(json, SubnetMetaData)
      purpose = subnetMetaData.purpose
      String targetName = subnetMetaData.target
      target = SubnetTarget.forText(targetName)
    } else {
      String[] splits = subnet.tags.find { it.key.equalsIgnoreCase('name') }?.value?.split(/\./)
      if (splits && splits.length == 3) {
        purpose = "${splits[1]} (${splits[0]})"
        target = null
      }
    }

    new SubnetData(purpose: purpose, target: target,
      subnetId: subnet.subnetId, state: subnet.state, vpcId: subnet.vpcId, cidrBlock: subnet.cidrBlock,
      availableIpAddressCount: subnet.availableIpAddressCount, availabilityZone: subnet.availabilityZone)
  }

  static class SubnetMetaData {
    String purpose
    String target
  }

}
