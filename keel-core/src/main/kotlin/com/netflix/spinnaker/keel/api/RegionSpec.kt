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
package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY

interface RegionSpec {
  val name: String
}

data class SubnetAwareRegionSpec(
  override val name: String,
  /**
   * If empty this implies the resource should use _all_ availability zones.
   */
  @JsonInclude(NON_EMPTY)
  val availabilityZones: Set<String> = emptySet()
) : RegionSpec

data class SimpleRegionSpec(
  override val name: String
) : RegionSpec
