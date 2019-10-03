/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.MultiRegion
import com.netflix.spinnaker.keel.model.Moniker
import de.danielbechler.diff.inclusion.Inclusion.EXCLUDED
import de.danielbechler.diff.introspection.ObjectDiffProperty

data class SecurityGroupSpec(
  override val moniker: Moniker,
  val locations: Locations,
  val vpcName: String?,
  val description: String?,
  val inboundRules: Set<SecurityGroupRule> = emptySet(),
  val overrides: Map<String, SecurityGroupOverride> = emptyMap()
) : MultiRegion {
  override val id = "${locations.accountName}:${moniker.name}"

  override val regionalIds = locations.regions.map { region ->
    "${locations.accountName}:$region:${moniker.name}"
  }.sorted()

  data class Locations(
    val accountName: String,
    val regions: Set<String>
  )
}

data class SecurityGroupOverride(
  val vpcName: String? = null,
  @get:ObjectDiffProperty(inclusion = EXCLUDED)
  val description: String? = null,
  val inboundRules: Set<SecurityGroupRule>? = null
)
