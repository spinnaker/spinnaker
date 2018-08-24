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

import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BlockDeviceConfigSpec extends Specification {

  @Shared
  static def defaultBlockDevice = new AmazonBlockDevice(deviceName: "/dev/sdb", size: 40)

  @Shared
  static def expectedD28xlargeBlockDevices = [
    new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0"),
    new AmazonBlockDevice(deviceName: "/dev/sdc", virtualName: "ephemeral1"),
    new AmazonBlockDevice(deviceName: "/dev/sdd", virtualName: "ephemeral2"),
    new AmazonBlockDevice(deviceName: "/dev/sde", virtualName: "ephemeral3"),
    new AmazonBlockDevice(deviceName: "/dev/sdf", virtualName: "ephemeral4"),
    new AmazonBlockDevice(deviceName: "/dev/sdg", virtualName: "ephemeral5"),
    new AmazonBlockDevice(deviceName: "/dev/sdh", virtualName: "ephemeral6"),
    new AmazonBlockDevice(deviceName: "/dev/sdi", virtualName: "ephemeral7"),
    new AmazonBlockDevice(deviceName: "/dev/sdj", virtualName: "ephemeral8"),
    new AmazonBlockDevice(deviceName: "/dev/sdk", virtualName: "ephemeral9"),
    new AmazonBlockDevice(deviceName: "/dev/sdl", virtualName: "ephemeral10"),
    new AmazonBlockDevice(deviceName: "/dev/sdm", virtualName: "ephemeral11"),
    new AmazonBlockDevice(deviceName: "/dev/sdn", virtualName: "ephemeral12"),
    new AmazonBlockDevice(deviceName: "/dev/sdo", virtualName: "ephemeral13"),
    new AmazonBlockDevice(deviceName: "/dev/sdp", virtualName: "ephemeral14"),
    new AmazonBlockDevice(deviceName: "/dev/sdq", virtualName: "ephemeral15"),
    new AmazonBlockDevice(deviceName: "/dev/sdr", virtualName: "ephemeral16"),
    new AmazonBlockDevice(deviceName: "/dev/sds", virtualName: "ephemeral17"),
    new AmazonBlockDevice(deviceName: "/dev/sdt", virtualName: "ephemeral18"),
    new AmazonBlockDevice(deviceName: "/dev/sdu", virtualName: "ephemeral19"),
    new AmazonBlockDevice(deviceName: "/dev/sdv", virtualName: "ephemeral20"),
    new AmazonBlockDevice(deviceName: "/dev/sdw", virtualName: "ephemeral21"),
    new AmazonBlockDevice(deviceName: "/dev/sdx", virtualName: "ephemeral22"),
    new AmazonBlockDevice(deviceName: "/dev/sdy", virtualName: "ephemeral23"),
  ]

  @Unroll
  void "should return block devices for instance type"() {

    DeployDefaults deployDefaults = new DeployDefaults(unknownInstanceTypeBlockDevice: unknownInstanceTypeBlockDevice)
    if (defaultVolumeType) {
      deployDefaults.defaultBlockDeviceType = defaultVolumeType
    }
    BlockDeviceConfig blockDeviceConfig = new BlockDeviceConfig(deployDefaults)

    expect:
    blockDevices == blockDeviceConfig.getBlockDevicesForInstanceType(instanceType)

    where:
    unknownInstanceTypeBlockDevice | defaultVolumeType | instanceType  || blockDevices
    null                           | null              | "wat"         || null
    defaultBlockDevice             | null              | "wat"         || [defaultBlockDevice]
    null                           | null              | "t2.small"    || []
    defaultBlockDevice             | null              | "t2.small"    || []
    null                           | null              | "m4.xlarge"   || [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 80, volumeType: "standard")]
    defaultBlockDevice             | null              | "m4.xlarge"   || [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 80, volumeType: "standard")]
    null                           | null              | "m4.large"    || [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 40, volumeType: "standard")]
    null                           | null              | "m4.16xlarge" || [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 120, volumeType: "standard")]
    null                           | null              | "c4.8xlarge"  || getExpectedBlockDevicesForEbsOnly("standard")
    null                           | null              | "c5.9xlarge"  || getExpectedBlockDevicesForEbsOnly("standard")
    null                           | null              | "m3.medium"   || [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0")]
    null                           | null              | "i2.2xlarge"  || [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0"), new AmazonBlockDevice(deviceName: "/dev/sdc", virtualName: "ephemeral1")]
    null                           | null              | "d2.8xlarge"  || expectedD28xlargeBlockDevices
    null                           | "gp2"             | "m4.xlarge"   || [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 80, volumeType: defaultVolumeType)]
    null                           | "gp2"             | "c4.8xlarge"  || getExpectedBlockDevicesForEbsOnly("gp2")
  }

  private Collection<AmazonBlockDevice> getExpectedBlockDevicesForEbsOnly(String volumeType) {
    [
      new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125, volumeType: volumeType),
      new AmazonBlockDevice(deviceName: "/dev/sdc", size: 125, volumeType: volumeType),
    ]
  }
}
