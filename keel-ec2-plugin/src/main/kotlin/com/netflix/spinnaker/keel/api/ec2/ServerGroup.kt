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
import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.BuildInfo
import com.netflix.spinnaker.keel.model.parseMoniker
import de.danielbechler.diff.inclusion.Inclusion.EXCLUDED
import de.danielbechler.diff.introspection.ObjectDiffProperty

data class ServerGroup(
  /**
   * This field is immutable, so we would never be reacting to a diff on it. If the name differs,
   * it's a different resource. Also, a server group name retrieved from CloudDriver will include
   * the sequence number. However, when we resolve desired state from a [ClusterSpec] this field
   * will _not_ include the sequence number. Having it on the model returned from CloudDriver is
   * useful for some things (e.g. specifying ancestor server group when red-blacking a new version)
   * but is meaningless for a diff.
   */
  @get:ObjectDiffProperty(inclusion = EXCLUDED)
  val name: String,
  val location: Location,
  val launchConfiguration: LaunchConfiguration,
  val capacity: Capacity = Capacity(1, 1, 1),
  val dependencies: ClusterDependencies = ClusterDependencies(),
  val health: Health = Health(),
  val scaling: Scaling = Scaling(),
  val tags: Map<String, String> = emptyMap(),
  @JsonIgnore
  @get:ObjectDiffProperty(inclusion = EXCLUDED)
  val buildInfo: BuildInfo? = null
) {
  init {
    require(
      capacity.desired != null && !scaling.hasScalingPolicies() ||
        capacity.desired == null && scaling.hasScalingPolicies()
    ) {
      "capacity.desired and auto-scaling policies are mutually exclusive"
    }
  }
}

val ServerGroup.moniker: Moniker
  get() = parseMoniker(name)

fun Iterable<ServerGroup>.byRegion(): Map<String, ServerGroup> =
  associateBy { it.location.region }
