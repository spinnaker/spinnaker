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
package com.netflix.spinnaker.keel.api.titus.cluster

import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Constraints
import com.netflix.spinnaker.keel.clouddriver.model.MigrationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.Resources
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.model.parseMoniker
import de.danielbechler.diff.inclusion.Inclusion
import de.danielbechler.diff.introspection.ObjectDiffProperty

data class TitusServerGroup(
  /**
   * This field is immutable, so we would never be reacting to a diff on it. If the name differs,
   * it's a different resource. Also, a server group name retrieved from CloudDriver will include
   * the sequence number. However, when we resolve desired state from a [ClusterSpec] this field
   * will _not_ include the sequence number. Having it on the model returned from CloudDriver is
   * useful for some things (e.g. specifying ancestor server group when red-blacking a new version)
   * but is meaningless for a diff.
   */
  @get:ObjectDiffProperty(inclusion = Inclusion.EXCLUDED)
  val name: String,
  val container: DigestProvider,
  val location: Location,
  val env: Map<String, String> = emptyMap(),
  val containerAttributes: Map<String, String> = emptyMap(),
  val resources: Resources = Resources(),
  val iamProfile: String,
  val entryPoint: String = "",
  val capacityGroup: String,
  val constraints: Constraints = Constraints(),
  val migrationPolicy: MigrationPolicy = MigrationPolicy(),
  val capacity: Capacity,
  val tags: Map<String, String> = emptyMap(),
  val dependencies: ClusterDependencies = ClusterDependencies(),
  val deferredInitialization: Boolean = true,
  val delayBeforeDisableSec: Int = 0,
  val delayBeforeScaleDownSec: Int = 0
)

val TitusServerGroup.moniker: Moniker
  get() = parseMoniker(name)

fun Iterable<TitusServerGroup>.byRegion(): Map<String, TitusServerGroup> =
  associateBy { it.location.region }

// todo eb: should this be more general?
data class Location(
  val account: String,
  val region: String
)
