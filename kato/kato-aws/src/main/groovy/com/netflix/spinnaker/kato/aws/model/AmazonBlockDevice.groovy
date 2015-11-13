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

import groovy.transform.Canonical

/**
 * Model for a block device mapping
 *
 * A block device mapping can either be an ephemeral device
 *   (deviceName + virtualName)
 * or an EBS device:
 *   (deviceName + size + (optionally) volumeType, deleteOnTermination, iops, snapshotId)
 *
 *
 */
@Canonical
class AmazonBlockDevice {

  // Required for all:
  /**
   * The name of the virtual device (ie. /dev/sdb, /dev/sdc)
   */
  String deviceName

  // Ephemeral device support:
  /**
   * The virtual name as it corresponds to the infrastructure. Only relevant for ephemeral devices.
   * Valid value examples: (ephemeral0, ephemeral1, etc...)
   */
  String virtualName

  // EBS support:
  /**
   * The size of the virtual device in Gigabytes (ie. 125)
   */
  Integer size

  /**
   * The EBS volume type (gp2, io1, standard)
   */
  String volumeType

  /**
   * Whether the EBS volume should be deleted on termination
   */
  Boolean deleteOnTermination

  /**
   * The IOPS for the volume
   */
  Integer iops

  /**
   * The snapshot id to mount as the EBS volume
   */
  String snapshotId
}
