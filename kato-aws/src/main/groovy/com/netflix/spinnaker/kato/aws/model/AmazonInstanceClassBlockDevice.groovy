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

/**
 * This class provides configuration for the block devices for a given instance class.
 * An instance class at AWS is the prefix of the instance type (ie. m1, m3, r3, i2, etc...)
 */
class AmazonInstanceClassBlockDevice {
  /**
   * The class of instance that corresponds to this configuration
   */
  String instanceClass

  /**
   * The block device mappings for this configuration
   */
  List<AmazonBlockDevice> blockDevices

  boolean handlesInstanceType(String instanceType) {
    instanceType && instanceType.startsWith("${instanceClass}.")
  }
}
