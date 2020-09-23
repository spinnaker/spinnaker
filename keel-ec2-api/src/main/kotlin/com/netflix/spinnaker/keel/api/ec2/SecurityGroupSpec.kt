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

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.schema.Optional

data class SecurityGroupSpec(
  override val moniker: Moniker,
  @Optional override val locations: SimpleLocations,
  val description: String?,
  val inboundRules: Set<SecurityGroupRule> = emptySet(),
  val overrides: Map<String, SecurityGroupOverride> = emptyMap()
) : Monikered, Locatable<SimpleLocations> {
  override val id = "${locations.account}:$moniker"
}

data class SecurityGroupOverride(
  val description: String? = null,
  val inboundRules: Set<SecurityGroupRule>? = null,
  val vpc: String? = null
)
