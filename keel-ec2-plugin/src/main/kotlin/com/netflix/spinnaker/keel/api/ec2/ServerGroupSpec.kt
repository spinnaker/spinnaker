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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Dependencies
import com.netflix.spinnaker.keel.api.ec2.cluster.Health
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.cluster.Scaling
import com.netflix.spinnaker.keel.model.Moniker

data class ServerGroupSpec(
  override val moniker: Moniker,
  val location: Location,
  val launchConfiguration: LaunchConfigurationSpec,
  val capacity: Capacity = Capacity(1, 1, 1),
  val dependencies: Dependencies = Dependencies(),
  val health: Health = Health(),
  val scaling: Scaling = Scaling(),
  val tags: Map<String, String> = emptyMap()
) : Monikered, ResourceSpec {
  @JsonIgnore
  override val id: String = "${location.accountName}:${location.region}:${moniker.name}"
}
