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

import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BlockDeviceConfigSpec extends Specification {

  @Shared static def expectedBlockDevicesForEbsOnly = [
    new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125),
    new AmazonBlockDevice(deviceName: "/dev/sdc", size: 125),
  ]

  @Shared static def expectedD28xlargeBlockDevices = [
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

    expect:
    blockDevices == BlockDeviceConfig.blockDevicesByInstanceType[instanceType]

    where:
    instanceType || blockDevices
    "wat"         | null
    "t2.small"    | []
    "m4.xlarge"   | expectedBlockDevicesForEbsOnly
    "c4.8xlarge"  | expectedBlockDevicesForEbsOnly
    "m3.medium"   | [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0")]
    "i2.2xlarge"  | [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0"), new AmazonBlockDevice(deviceName: "/dev/sdc", virtualName: "ephemeral1")]
    "d2.8xlarge"  | expectedD28xlargeBlockDevices
  }
}
