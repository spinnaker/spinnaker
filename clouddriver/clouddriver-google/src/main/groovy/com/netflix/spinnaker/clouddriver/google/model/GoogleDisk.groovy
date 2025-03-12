/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true)
class GoogleDisk {
  GoogleDiskType type
  Long sizeGb
  // This is the unqualified name of an indexed image; just like the `image` defined on BaseGoogleInstanceDescription.
  // It should not be specified on the first persistent disk (since that is the boot disk and the image is specified at
  // the top-level of the request). It is required on all the remaining persistent disks.
  String sourceImage
  boolean autoDelete = true

  // Unique disk device name addressable by a Linux OS in /dev/disk/by-id/google-* in the running instance.
  // Used to reference disk for mounting, resizing, etc.
  // Only applicable for persistent disks.
  String deviceName

  Map<String, String> labels

  void setType(String type) {
    this.type = GoogleDiskType.fromValue(type)
  }

  boolean isPersistent() {
    type?.persistent
  }
}
