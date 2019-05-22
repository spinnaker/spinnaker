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
package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.keel.api.ec2.image.ImageProvider

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClusterSpec(
  val moniker: Cluster.Moniker,
  val location: Cluster.Location,
  val launchConfiguration: LaunchConfigurationSpec,
  val capacity: Capacity = Capacity(1, 1, 1),
  val dependencies: Cluster.Dependencies = Cluster.Dependencies(),
  val health: Cluster.Health = Cluster.Health(),
  val scaling: Cluster.Scaling = Cluster.Scaling(),
  val tags: Map<String, String> = emptyMap()
) {
  data class LaunchConfigurationSpec(
    val imageProvider: ImageProvider,
    val instanceType: String,
    val ebsOptimized: Boolean,
    val iamRole: String,
    val keyPair: String,
    val instanceMonitoring: Boolean = false,
    val ramdiskId: String? = null
  ) {
    fun generateLaunchConfiguration(imageId: String): Cluster.LaunchConfiguration {
      return Cluster.LaunchConfiguration(
        imageId = imageId,
        instanceType = instanceType,
        ebsOptimized = ebsOptimized,
        iamRole = iamRole,
        keyPair = keyPair,
        instanceMonitoring = instanceMonitoring,
        ramdiskId = ramdiskId
      )
    }
  }
}
