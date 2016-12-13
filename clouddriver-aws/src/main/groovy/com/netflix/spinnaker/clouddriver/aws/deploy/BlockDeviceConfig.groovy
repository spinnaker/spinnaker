/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice

class BlockDeviceConfig {

  static def List<AmazonBlockDevice> enumeratedBlockDevicesWithVirtualName(int size) {
    def letters = ('a'..'z').collect { it }
    (0..<size).collect {
      def letter = letters[it + 1]
      new AmazonBlockDevice(deviceName: "/dev/sd${letter}", virtualName: "ephemeral${it}")
    }
  }

  static def defaultBlockDevicesForEbsOnly() {
    [
      new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125),
      new AmazonBlockDevice(deviceName: "/dev/sdc", size: 125),
    ]
  }

  static def sizedBlockDevicesForEbs(int capacity) {
    [
      new AmazonBlockDevice(deviceName: "/dev/sdb", size: capacity)
    ]
  }

  static final def blockDevicesByInstanceType = [
    "c3.large": enumeratedBlockDevicesWithVirtualName(2),
    "c3.xlarge": enumeratedBlockDevicesWithVirtualName(2),
    "c3.2xlarge": enumeratedBlockDevicesWithVirtualName(2),
    "c3.4xlarge": enumeratedBlockDevicesWithVirtualName(2),
    "c3.8xlarge": enumeratedBlockDevicesWithVirtualName(2),
    "c4.large" : defaultBlockDevicesForEbsOnly(),
    "c4.xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.2xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.4xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.8xlarge" : defaultBlockDevicesForEbsOnly(),
    "d2.xlarge" : enumeratedBlockDevicesWithVirtualName(3),
    "d2.2xlarge" : enumeratedBlockDevicesWithVirtualName(6),
    "d2.4xlarge" : enumeratedBlockDevicesWithVirtualName(12),
    "d2.8xlarge" : enumeratedBlockDevicesWithVirtualName(24),
    "i2.2xlarge" : enumeratedBlockDevicesWithVirtualName(2),
    "i2.xlarge" : enumeratedBlockDevicesWithVirtualName(1),
    "i2.4xlarge" : enumeratedBlockDevicesWithVirtualName(4),
    "i2.8xlarge" : enumeratedBlockDevicesWithVirtualName(8),
    "m3.medium" : enumeratedBlockDevicesWithVirtualName(1),
    "m3.large" : enumeratedBlockDevicesWithVirtualName(1),
    "m3.xlarge" : enumeratedBlockDevicesWithVirtualName(2),
    "m3.2xlarge" : enumeratedBlockDevicesWithVirtualName(2),
    "m4.large" : sizedBlockDevicesForEbs(40),
    "m4.xlarge" : sizedBlockDevicesForEbs(80),
    "m4.2xlarge" : sizedBlockDevicesForEbs(80),
    "m4.4xlarge" : sizedBlockDevicesForEbs(120),
    "m4.10xlarge" : sizedBlockDevicesForEbs(120),
    "m4.16xlarge" : sizedBlockDevicesForEbs(120),
    "r3.large": enumeratedBlockDevicesWithVirtualName(1),
    "r3.xlarge": enumeratedBlockDevicesWithVirtualName(1),
    "r3.2xlarge": enumeratedBlockDevicesWithVirtualName(1),
    "r3.4xlarge": enumeratedBlockDevicesWithVirtualName(1),
    "r3.8xlarge": enumeratedBlockDevicesWithVirtualName(2),
    "r4.large" : sizedBlockDevicesForEbs(40),
    "r4.xlarge" : sizedBlockDevicesForEbs(80),
    "r4.2xlarge" : sizedBlockDevicesForEbs(80),
    "r4.4xlarge" : sizedBlockDevicesForEbs(120),
    "r4.8xlarge" : sizedBlockDevicesForEbs(120),
    "r4.16xlarge" : sizedBlockDevicesForEbs(120),
    "t2.micro" : [],
    "t2.small" : [],
    "t2.medium" : [],
    "t2.large" : [],
  ].asImmutable()

}
