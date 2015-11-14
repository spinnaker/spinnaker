package com.netflix.spinnaker.kato.aws.deploy

import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice

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

  static final def blockDevicesByInstanceType = [
    "t2.micro" : defaultBlockDevicesForEbsOnly(),
    "t2.small" : defaultBlockDevicesForEbsOnly(),
    "t2.medium" : defaultBlockDevicesForEbsOnly(),
    "t2.large" : defaultBlockDevicesForEbsOnly(),
    "m4.large" : defaultBlockDevicesForEbsOnly(),
    "m4.xlarge" : defaultBlockDevicesForEbsOnly(),
    "m4.2xlarge" : defaultBlockDevicesForEbsOnly(),
    "m4.4xlarge" : defaultBlockDevicesForEbsOnly(),
    "m4.10xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.large" : defaultBlockDevicesForEbsOnly(),
    "c4.xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.2xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.4xlarge" : defaultBlockDevicesForEbsOnly(),
    "c4.8xlarge" : defaultBlockDevicesForEbsOnly(),
    "m3.medium" : enumeratedBlockDevicesWithVirtualName(1),
    "m3.large" : enumeratedBlockDevicesWithVirtualName(2),
    "m3.xlarge" : enumeratedBlockDevicesWithVirtualName(2),
    "i2.xlarge" : enumeratedBlockDevicesWithVirtualName(1),
    "i2.2xlarge" : enumeratedBlockDevicesWithVirtualName(2),
    "i2.4xlarge" : enumeratedBlockDevicesWithVirtualName(4),
    "i2.8xlarge" : enumeratedBlockDevicesWithVirtualName(8),
    "d2.xlarge" : enumeratedBlockDevicesWithVirtualName(3),
    "d2.2xlarge" : enumeratedBlockDevicesWithVirtualName(6),
    "d2.4xlarge" : enumeratedBlockDevicesWithVirtualName(12),
    "d2.8xlarge" : enumeratedBlockDevicesWithVirtualName(24),
  ].asImmutable()

}
