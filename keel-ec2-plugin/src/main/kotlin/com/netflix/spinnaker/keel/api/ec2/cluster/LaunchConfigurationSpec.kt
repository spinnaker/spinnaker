/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.api.ec2.cluster

import com.netflix.spinnaker.keel.api.ec2.image.ImageProvider

data class LaunchConfigurationSpec(
  val imageProvider: ImageProvider,
  val instanceType: String,
  val ebsOptimized: Boolean,
  val iamRole: String,
  val keyPair: String,
  val instanceMonitoring: Boolean = false,
  val ramdiskId: String? = null
) {
  fun generateLaunchConfiguration(imageId: String, appVersion: String) =
    LaunchConfiguration(
      imageId = imageId,
      appVersion = appVersion,
      instanceType = instanceType,
      ebsOptimized = ebsOptimized,
      iamRole = iamRole,
      keyPair = keyPair,
      instanceMonitoring = instanceMonitoring,
      ramdiskId = ramdiskId
    )
}
